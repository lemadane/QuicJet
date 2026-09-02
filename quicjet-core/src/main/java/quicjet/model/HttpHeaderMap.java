package quicjet.model;

import java.util.*;

public class HttpHeaderMap {
    private final Map<String, List<String>> headers = new LinkedHashMap<>();

    public HttpHeaderMap() {}

    public HttpHeaderMap(Map<String, List<String>> map) {
        if (map != null) {
            map.forEach((k, v) -> headers.put(k.toLowerCase(Locale.ROOT), new ArrayList<>(v)));
        }
    }

    public HttpHeaderMap add(String name, String value) {
        if (name == null || value == null) return this;
        headers.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(value);
        return this;
    }

    public HttpHeaderMap set(String name, String value) {
        if (name == null) return this;
        List<String> list = new ArrayList<>();
        if (value != null) list.add(value);
        headers.put(name.toLowerCase(Locale.ROOT), list);
        return this;
    }

    public Optional<String> firstValue(String name) {
        List<String> list = headers.get(name.toLowerCase(Locale.ROOT));
        if (list != null && !list.isEmpty()) {
            return Optional.of(list.get(0));
        }
        return Optional.empty();
    }

    public List<String> allValues(String name) {
        List<String> list = headers.get(name.toLowerCase(Locale.ROOT));
        return list != null ? Collections.unmodifiableList(list) : List.of();
    }

    public Map<String, List<String>> asMap() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        headers.forEach((k, v) -> copy.put(k, Collections.unmodifiableList(v)));
        return Collections.unmodifiableMap(copy);
    }
}
