package vn.farm.farmdevice.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import vn.farm.core.BusinessException;

public final class Paging {
    public static final int MAX_PAGE_SIZE = 100;

    private Paging() {}

    public static Pageable of(int page, int pageSize, Sort sort) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw BusinessException.invalid("INVALID_QUERY", "page phải >= 1 và pageSize trong khoảng 1..100.");
        }
        return PageRequest.of(page - 1, pageSize, sort);
    }

    /** Đọc enum từ query string; rỗng = không lọc; sai giá trị = 400 INVALID_QUERY. */
    public static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String name) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.invalid("INVALID_QUERY", "Giá trị của '" + name + "' không hợp lệ.");
        }
    }
}
