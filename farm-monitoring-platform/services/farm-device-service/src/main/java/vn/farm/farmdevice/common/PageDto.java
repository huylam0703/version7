package vn.farm.farmdevice.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Danh sách phân trang theo PLATFORM-CONVENTIONS: { items, page, pageSize, total }. page bắt đầu từ 1. */
public record PageDto<T>(List<T> items, int page, int pageSize, long total) {
    public static <S, T> PageDto<T> of(Page<S> p, Function<S, T> mapper) {
        return new PageDto<>(p.getContent().stream().map(mapper).toList(),
                p.getNumber() + 1, p.getSize(), p.getTotalElements());
    }
}
