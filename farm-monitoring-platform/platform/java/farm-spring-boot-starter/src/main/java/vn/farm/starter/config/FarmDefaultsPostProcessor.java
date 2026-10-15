package vn.farm.starter.config;

import java.io.IOException;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

public class FarmDefaultsPostProcessor implements EnvironmentPostProcessor, Ordered {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        try {
            List<PropertySource<?>> sources =
                    new YamlPropertySourceLoader().load("farm-defaults", new ClassPathResource("farm-defaults.yml"));
            sources.forEach(env.getPropertySources()::addLast);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được farm-defaults.yml", e);
        }
    }

    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}