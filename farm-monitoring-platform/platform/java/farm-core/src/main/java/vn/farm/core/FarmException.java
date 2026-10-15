package vn.farm.core;

public class FarmException extends RuntimeException {
    private final String code;
    private final int status;

    public FarmException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public static FarmException notFound(String code, String msg)   { return new FarmException(404, code, msg); }
    public static FarmException badRequest(String code, String msg) { return new FarmException(400, code, msg); }
    public static FarmException conflict(String code, String msg)   { return new FarmException(409, code, msg); }
    public String getCode() { return code; }
    public int getStatus() { return status; }
}