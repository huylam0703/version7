package vn.farm.core;

public class BusinessException extends RuntimeException {
    public enum Type { VALIDATION, NOT_FOUND, CONFLICT, UNAUTHORIZED, FORBIDDEN, FAILURE }

    private final String code;
    private final Type type;

    public BusinessException(Type type, String code, String message) {
        super(message);
        this.type = type;
        this.code = code;
    }

    public static BusinessException notFound(String code, String msg) { return new BusinessException(Type.NOT_FOUND, code, msg); }
    public static BusinessException conflict(String code, String msg) { return new BusinessException(Type.CONFLICT, code, msg); }
    public static BusinessException invalid(String code, String msg)  { return new BusinessException(Type.VALIDATION, code, msg); }

    public String getCode() { return code; }
    public Type getType() { return type; }
}