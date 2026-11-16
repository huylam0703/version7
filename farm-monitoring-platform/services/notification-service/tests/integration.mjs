import assert from 'node:assert/strict';
import { execFileSync, spawn } from 'node:child_process';
import { generateKeyPairSync, randomUUID, sign } from 'node:crypto';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import http from 'node:http';
import net from 'node:net';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Real SQL Server and RabbitMQ, with local-only Auth and SMTP test servers.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const project = path.join(root, 'NotificationService.Api', 'NotificationService.Api.csproj');
const dll = path.join(root, 'NotificationService.Api', 'bin', 'Debug', 'net10.0', 'NotificationService.Api.dll');
const sqlContainer = process.env.NOTI_TEST_SQL_CONTAINER;
const sqlPassword = process.env.NOTI_TEST_SQL_PASSWORD;
const rabbitUser = process.env.NOTI_TEST_RABBIT_USER;
const rabbitPassword = process.env.NOTI_TEST_RABBIT_PASSWORD;
assert(sqlContainer && sqlPassword && rabbitUser && rabbitPassword,
  'Set NOTI_TEST_SQL_CONTAINER, NOTI_TEST_SQL_PASSWORD, NOTI_TEST_RABBIT_USER and NOTI_TEST_RABBIT_PASSWORD');
const rabbitUrl = process.env.NOTI_TEST_RABBIT_URL || 'http://127.0.0.1:15672';
const sqlAddress = process.env.NOTI_TEST_SQL_ADDRESS || 'localhost,14331';
const suffix = randomUUID().replaceAll('-', '');
const database = `noti_test_${suffix}`;
const freshDatabase = `noti_fresh_${suffix}`;
const vhost = `noti-test-${suffix}`;
const queue = 'notification.events';
const adminId = randomUUID();
const secondAdminId = randomUUID();
const temporary = mkdtempSync(path.join(tmpdir(), 'noti-test-'));
const applications = [];
const receivedMail = [];
const deadLetters = [];
let authUnavailable = false;
let smtpUnavailable = false;
let tokenRequests = 0;
let recipientRequests = 0;
let recipientCount = 1;
const sockets = new Set();

const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const jwk = { ...publicKey.export({ format: 'jwk' }), kid: 'integration', use: 'sig', alg: 'RS256' };
const jwt = (userId = adminId, role = 'ADMIN') => {
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const now = Math.floor(Date.now() / 1000);
  const unsigned = `${encode({ alg: 'RS256', kid: jwk.kid })}.${encode({
    sub: userId, role, iss: 'farm-auth', aud: 'farm-api', nbf: now - 10, exp: now + 3600,
  })}`;
  return `${unsigned}.${sign('RSA-SHA256', Buffer.from(unsigned), privateKey).toString('base64url')}`;
};

const auth = http.createServer(async (request, response) => {
  response.setHeader('Content-Type', 'application/json');
  if (request.url === '/.well-known/openid-configuration') {
    response.end(JSON.stringify({ issuer: 'farm-auth', jwks_uri: `${authUrl}/keys` }));
  } else if (request.url === '/keys') {
    response.end(JSON.stringify({ keys: [jwk] }));
  } else if (request.url === '/connect/token') {
    tokenRequests++;
    let body = '';
    for await (const chunk of request) body += chunk;
    const form = new URLSearchParams(body);
    if (form.get('grant_type') !== 'client_credentials' ||
        form.get('client_id') !== 'notification-service' || form.get('client_secret') !== 'integration-secret') {
      response.writeHead(401).end('{}');
      return;
    }
    response.end(JSON.stringify({ access_token: 'integration-service-token', token_type: 'Bearer' }));
  } else if (request.url === '/internal/v1/users/notification-recipients') {
    recipientRequests++;
    if (request.headers.authorization !== 'Bearer integration-service-token') {
      response.writeHead(401).end('{}');
      return;
    }
    if (authUnavailable) response.writeHead(503).end('{}');
    else response.end(JSON.stringify({ result: [
      { userId: adminId, email: 'admin@noti.test' },
      { userId: secondAdminId, email: 'second@noti.test' },
    ].slice(0, recipientCount) }));
  } else response.writeHead(404).end('{}');
});

const smtp = net.createServer(socket => {
  sockets.add(socket);
  socket.on('close', () => sockets.delete(socket));
  socket.on('error', () => {});
  socket.write('220 local notification test SMTP\r\n');
  let buffer = '';
  let data = false;
  let message = '';
  socket.on('data', chunk => {
    buffer += chunk.toString();
    let end;
    while ((end = buffer.indexOf('\r\n')) >= 0) {
      const line = buffer.slice(0, end);
      buffer = buffer.slice(end + 2);
      if (data) {
        if (line === '.') {
          data = false;
          receivedMail.push(message);
          message = '';
          socket.write('250 accepted\r\n');
        } else message += `${line}\r\n`;
      } else if (/^(EHLO|HELO)/i.test(line)) socket.write('250 localhost\r\n');
      else if (/^MAIL FROM/i.test(line)) socket.write(smtpUnavailable ? '451 unavailable\r\n' : '250 OK\r\n');
      else if (/^DATA$/i.test(line)) {
        data = true;
        socket.write('354 send data\r\n');
      } else if (/^QUIT$/i.test(line)) socket.end('221 bye\r\n');
      else socket.write('250 OK\r\n');
    }
  });
});

const listen = server => new Promise(resolve => server.listen(0, '127.0.0.1', () => resolve(server.address().port)));
const authUrl = `http://127.0.0.1:${await listen(auth)}`;
const smtpPort = await listen(smtp);
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
async function waitFor(check, label, timeout = 30000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    if (await check()) return;
    await pause(300);
  }
  throw new Error(`Timed out: ${label}`);
}
function sql(statement, target = database) {
  return execFileSync('docker', ['exec', '-i', '-e', 'SQLCMDPASSWORD', sqlContainer,
    '/opt/mssql-tools18/bin/sqlcmd', '-C', '-S', 'localhost', '-U', 'sa', '-d', target,
    '-b', '-I', '-h', '-1', '-W'], { input: `SET NOCOUNT ON;\n${statement}\nGO\n`, encoding: 'utf8',
      env: { ...process.env, SQLCMDPASSWORD: sqlPassword } }).trim();
}
async function rabbit(route, method = 'GET', body) {
  const response = await fetch(`${rabbitUrl}/api/${route}`, {
    method, headers: { Authorization: `Basic ${Buffer.from(`${rabbitUser}:${rabbitPassword}`).toString('base64')}`,
      'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) throw new Error(`RabbitMQ ${method} ${route} returned ${response.status}`);
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
function event(type = 'alert.created', payload = {}) {
  return { eventId: randomUUID(), eventType: type, version: 1, producer: 'integration',
    occurredAt: new Date().toISOString(), payload: type === 'alert.created' ? {
      alertId: randomUUID(), type: 'OUT_OF_RANGE', severity: 'HIGH', message: 'Integration alert', ...payload,
    } : { missionId: randomUUID(), farmId: randomUUID(), uavId: randomUUID(), gatewayId: randomUUID(),
      newStatus: 'FAILED', changedAt: new Date().toISOString(), ...payload } };
}
async function publish(message) {
  const result = await rabbit(`exchanges/${vhost}/farm.events/publish`, 'POST', {
    properties: { delivery_mode: 2, message_id: message.eventId }, routing_key: message.eventType,
    payload: JSON.stringify(message), payload_encoding: 'string',
  });
  assert.equal(result.routed, true);
}
async function collectDeadLetters() {
  const messages = await rabbit(`queues/${vhost}/${queue}.dlq/get`, 'POST', {
    count: 100, ackmode: 'ack_requeue_false', encoding: 'auto', truncate: 50000,
  });
  deadLetters.push(...messages);
}
async function start(overrides = {}) {
  const reservation = net.createServer();
  const port = await listen(reservation);
  await new Promise(resolve => reservation.close(resolve));
  const app = { url: `http://127.0.0.1:${port}`, logs: '' };
  app.process = spawn('dotnet', [dll, '--urls', app.url], {
    cwd: path.dirname(project), windowsHide: true,
    env: { ...process.env, ASPNETCORE_ENVIRONMENT: 'Development',
      ConnectionStrings__Default: `Server=${sqlAddress};Database=${database};User Id=sa;Password=${sqlPassword};TrustServerCertificate=True`,
      Jwt__MetadataAddress: `${authUrl}/.well-known/openid-configuration`,
      RabbitMq__Enabled: 'true', RabbitMq__Host: 'localhost', RabbitMq__VirtualHost: vhost,
      RabbitMq__User: rabbitUser, RabbitMq__Password: rabbitPassword,
      Notifications__RecipientSource: 'UserAuth', Notifications__MissionSource: 'Direct',
      UserAuth__BaseUrl: `${authUrl}/`, UserAuth__ClientSecret: 'integration-secret',
      Email__Enabled: 'true', Email__Host: '127.0.0.1', Email__Port: String(smtpPort),
      Email__EnableSsl: 'false', Email__From: 'notifications@noti.test',
      Email__RetrySeconds: '2', Email__MaxAttempts: '3',
      Logging__LogLevel__Default: 'Warning', ...overrides },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  applications.push(app);
  app.process.stdout.on('data', data => { app.logs += data; });
  app.process.stderr.on('data', data => { app.logs += data; });
  await waitFor(async () => {
    if (app.process.exitCode !== null) throw new Error(`Application exited: ${app.logs}`);
    try { return (await fetch(`${app.url}/health`)).ok; } catch { return false; }
  }, 'application startup');
  return app;
}
async function stop(app) {
  if (app.process.exitCode !== null || app.process.signalCode !== null) return;
  const exited = new Promise(resolve => app.process.once('exit', resolve));
  app.process.kill();
  await exited;
}
async function api(app, route, method = 'GET', token = jwt()) {
  return fetch(`${app.url}/api/notifications${route}`, { method,
    headers: token ? { Authorization: `Bearer ${token}` } : {} });
}
const processed = id => Number(sql(`SELECT COUNT(*) FROM ProcessedNotificationEvents WHERE EventId='${id}'`)) === 1;
const emailSent = id => Number(sql(`SELECT COUNT(*) FROM NotificationEmails WHERE EventId='${id}' AND SentAt IS NOT NULL`)) > 0;

try {
  sql(`CREATE DATABASE [${database}]; CREATE DATABASE [${freshDatabase}];`, 'master');
  await rabbit(`vhosts/${vhost}`, 'PUT', {});
  await rabbit(`permissions/${vhost}/${encodeURIComponent(rabbitUser)}`, 'PUT',
    { configure: '.*', write: '.*', read: '.*' });

  // Exercise upgrade from the earlier SourceEventId schema, including backfill.
  const migrationScript = path.join(temporary, 'previous.sql');
  execFileSync('dotnet', ['ef', 'migrations', 'script', '0', '20261116150000_AddSourceEventId',
    '--no-build', '--project', project, '--output', migrationScript], {
    env: { ...process.env, ConnectionStrings__Default: 'Server=localhost;Database=unused;Integrated Security=true;TrustServerCertificate=True' },
    stdio: 'pipe',
  });
  sql(readFileSync(migrationScript, 'utf8').replace(/^\uFEFF/, ''));
  const legacyId = randomUUID();
  sql(`INSERT INTO Notifications (Id,UserId,Title,Message,Type,IsRead,CreatedAt,SourceEventId)
       VALUES (NEWID(),'${adminId}','Legacy','Legacy','ALERT',0,SYSUTCDATETIME(),'${legacyId}')`);
  let app = await start();
  assert(processed(legacyId));
  assert.equal(Number(sql('SELECT COUNT(*) FROM NotificationEmails')), 0);
  console.log('PASS upgrade migration backfills processed events without sending historical email');

  const fresh = await start({ ConnectionStrings__Default:
    `Server=${sqlAddress};Database=${freshDatabase};User Id=sa;Password=${sqlPassword};TrustServerCertificate=True`,
    RabbitMq__Enabled: 'false', Email__Enabled: 'false' });
  assert.equal(Number(sql("SELECT COUNT(*) FROM sys.tables WHERE name IN ('Notifications','NotificationEmails','ProcessedNotificationEvents')", freshDatabase)), 3);
  await stop(fresh);
  console.log('PASS migrations apply to a fresh database');

  await waitFor(async () => {
    try { return (await rabbit(`queues/${vhost}/${queue}`)).consumers === 1; } catch { return false; }
  }, 'RabbitMQ consumer');
  assert.equal((await api(app, '/me', 'GET', '')).status, 401);
  const sample = event();
  await publish(sample);
  await waitFor(() => emailSent(sample.eventId), 'event and SMTP delivery');
  assert(tokenRequests > 0 && recipientRequests > 0);
  assert.equal(receivedMail.length, 1);
  assert(receivedMail[0].includes('admin@noti.test'));
  let items = await (await api(app, '/me')).json();
  const item = items.find(value => value.sourceEventId === sample.eventId);
  assert(item);
  assert.equal((await api(app, `/${item.id}/read`, 'PATCH', jwt(randomUUID(), 'OPERATOR'))).status, 404);
  assert.equal((await api(app, `/${item.id}/read`, 'PATCH')).status, 200);
  assert.equal((await api(app, `/${item.id}`, 'DELETE')).status, 204);
  await publish(sample);
  await pause(1200);
  assert.equal(Number(sql(`SELECT COUNT(*) FROM Notifications WHERE SourceEventId='${sample.eventId}'`)), 0);
  assert.equal(Number(sql(`SELECT COUNT(*) FROM NotificationEmails WHERE EventId='${sample.eventId}'`)), 1);
  console.log('PASS service-token lookup, web ownership, SMTP delivery and replay after deletion');

  const otherApp = await start();
  const concurrent = event();
  await Promise.all(Array.from({ length: 12 }, () => publish(concurrent)));
  await waitFor(() => emailSent(concurrent.eventId), 'concurrent delivery');
  await pause(1200);
  assert.equal(Number(sql(`SELECT COUNT(*) FROM Notifications WHERE SourceEventId='${concurrent.eventId}'`)), 1);
  assert.equal(Number(sql(`SELECT COUNT(*) FROM NotificationEmails WHERE EventId='${concurrent.eventId}'`)), 1);
  assert.equal(receivedMail.length, 2);
  console.log('PASS two consumers and two email workers avoid duplicate persisted delivery');

  const malformed = { eventId: randomUUID(), eventType: 'alert.created', version: 1, payload: [] };
  await publish(malformed);
  await waitFor(async () => { await collectDeadLetters();
    return deadLetters.some(m => JSON.parse(m.payload).eventId === malformed.eventId);
  }, 'invalid event dead letter');
  assert(!processed(malformed.eventId));
  console.log('PASS malformed event goes directly to DLQ');

  authUnavailable = true;
  const retry = event();
  await publish(retry);
  await waitFor(async () => {
    const state = await rabbit(`queues/${vhost}/${queue}.retry`);
    return state.messages > 0;
  }, 'recipient lookup retry');
  assert(!processed(retry.eventId));
  authUnavailable = false;
  await waitFor(() => emailSent(retry.eventId), 'recovery after Auth outage');
  console.log('PASS temporary Auth failure retries without partial web/email state');

  authUnavailable = true;
  const exhausted = event();
  await publish(exhausted);
  await waitFor(async () => { await collectDeadLetters();
    return deadLetters.some(m => JSON.parse(m.payload).eventId === exhausted.eventId);
  }, 'bounded event retries', 45000);
  const exhaustedMessage = deadLetters.find(m => JSON.parse(m.payload).eventId === exhausted.eventId);
  assert.equal(exhaustedMessage.properties.headers['x-notification-attempt'], 3);
  authUnavailable = false;
  await publish(exhausted);
  await waitFor(() => emailSent(exhausted.eventId), 'replay after resolving Auth failure');
  console.log('PASS three failed attempts end in DLQ and replay succeeds after repair');

  smtpUnavailable = true;
  const emailRetry = event();
  await publish(emailRetry);
  await waitFor(() => Number(sql(`SELECT COUNT(*) FROM NotificationEmails WHERE EventId='${emailRetry.eventId}' AND Attempts=1 AND LastError IS NOT NULL`)) === 1,
    'SMTP failure persisted');
  await stop(otherApp);
  await stop(app);
  assert(processed(emailRetry.eventId));
  smtpUnavailable = false;
  app = await start();
  await waitFor(() => emailSent(emailRetry.eventId), 'email retry after process restart');
  assert.equal(Number(sql(`SELECT Attempts FROM NotificationEmails WHERE EventId='${emailRetry.eventId}'`)), 2);
  console.log('PASS SMTP failure retains web notification and email job survives restart');

  smtpUnavailable = true;
  const failedEmail = event();
  await publish(failedEmail);
  await waitFor(() => Number(sql(`SELECT COUNT(*) FROM NotificationEmails WHERE EventId='${failedEmail.eventId}' AND FailedAt IS NOT NULL AND Attempts=3`)) === 1,
    'email attempts exhausted');
  smtpUnavailable = false;
  console.log('PASS failed email stops after configured maximum attempts');

  recipientCount = 2;
  const multiple = event();
  await publish(multiple);
  await waitFor(() => Number(sql(`SELECT COUNT(*) FROM NotificationEmails WHERE EventId='${multiple.eventId}' AND SentAt IS NOT NULL`)) === 2,
    'multiple admin recipients');
  assert.equal(Number(sql(`SELECT COUNT(*) FROM Notifications WHERE SourceEventId='${multiple.eventId}'`)), 2);
  recipientCount = 1;
  console.log('PASS one web notification and email per admin');

  const mission = event('mission.status_changed', { newStatus: 'PARTIAL' });
  const missionAlert = event('alert.created', { type: 'COLLECTION_FAILED', missionId: mission.payload.missionId });
  await publish(mission);
  await publish(missionAlert);
  await waitFor(() => emailSent(mission.eventId), 'direct mission notification');
  await pause(1000);
  assert(!processed(missionAlert.eventId));
  await stop(app);
  app = await start({ Notifications__MissionSource: 'Alert' });
  const viaAlert = event('alert.created', { type: 'MISSION_FAILED', missionId: randomUUID() });
  const ignoredDirect = event('mission.status_changed');
  await publish(viaAlert);
  await publish(ignoredDirect);
  await waitFor(() => emailSent(viaAlert.eventId), 'alert mission notification');
  await pause(1000);
  assert(!processed(ignoredDirect.eventId));
  console.log('PASS explicit MissionSource avoids duplicate mission notification paths');

  const connections = await rabbit('connections');
  for (const connection of connections.filter(c => c.vhost === vhost))
    await rabbit(`connections/${encodeURIComponent(connection.name)}`, 'DELETE');
  await pause(6000);
  const afterReconnect = event();
  await publish(afterReconnect);
  await waitFor(() => emailSent(afterReconnect.eventId), 'RabbitMQ reconnect');
  console.log('PASS consumer reconnects and resumes delivery after connection loss');
  console.log('All notification integration scenarios passed. SMTP messages remained in the local test sink.');
} finally {
  for (const app of applications) await stop(app);
  for (const socket of sockets) socket.destroy();
  await Promise.all([new Promise(resolve => auth.close(resolve)), new Promise(resolve => smtp.close(resolve))]);
  try { await rabbit(`vhosts/${vhost}`, 'DELETE'); } catch (error) { console.error(error.message); }
  for (const name of [database, freshDatabase]) {
    try { sql(`IF DB_ID('${name}') IS NOT NULL BEGIN ALTER DATABASE [${name}] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [${name}]; END`, 'master'); }
    catch { console.error(`Could not remove test database ${name}`); }
  }
  rmSync(temporary, { recursive: true, force: true });
}
