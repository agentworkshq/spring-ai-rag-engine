package dev.agentworkshq.rag.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.agentworkshq.rag.web.RagWebProperties;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.util.unit.DataSize;

class ReadmePropertiesTest {

    private static final Path README = Path.of("..", "README.md");
    private static final String HEADING = "## Configuration reference";
    private static final Pattern NAME = Pattern.compile("`(rag-engine\\.[^`]*)`");
    private static final Pattern CODE = Pattern.compile("`([^`]*)`");
    private static final String SOURCE_PREFIX = "rag-engine.sources[].";

    private static final List<String> tableLines = new ArrayList<>();
    private static final Map<String, String> documentedDefaults = new LinkedHashMap<>();

    @BeforeAll
    static void readTable() throws IOException {
        assertThat(Files.isRegularFile(README))
                .as("README not found at %s, run the test from the module directory", README.toAbsolutePath())
                .isTrue();
        List<String> lines = Files.readAllLines(README);
        int heading = lines.indexOf(HEADING);
        assertThat(heading).as("README has no \"%s\" section", HEADING).isNotNegative();
        for (String line : lines.subList(heading + 1, lines.size())) {
            if (line.startsWith("## ")) {
                break;
            }
            if (line.startsWith("|")) {
                tableLines.add(line);
            }
        }
        for (String line : tableLines) {
            String[] cells = line.split("\\|");
            Matcher name = NAME.matcher(cells[1]);
            if (name.find()) {
                assertThat(documentedDefaults.put(name.group(1), cells[3].strip()))
                        .as("%s is listed twice", name.group(1)).isNull();
            }
        }
        assertThat(documentedDefaults).as("the configuration table has no properties").isNotEmpty();
    }

    @Test
    void everyPropertyIsDocumentedAndNothingElse() {
        assertThat(documentedDefaults.keySet()).containsExactlyInAnyOrderElementsOf(properties().keySet());
        for (String line : tableLines) {
            Matcher name = NAME.matcher(line);
            while (name.find()) {
                assertThat(properties()).containsKey(name.group(1));
            }
        }
    }

    @Test
    void everyDocumentedNameBinds() {
        documentedDefaults.keySet().forEach(name -> {
            Class<?> type = properties().get(name);
            assertThat(type).as("%s is not a property", name).isNotNull();
            // an enum with one constant has no other value, so a bound name is shown by a rejected one
            if (type.isEnum() && type.getEnumConstants().length == 1) {
                assertThatThrownBy(() -> value(name, Map.of(name, "other"))).as(name)
                        .isInstanceOf(BindException.class);
                return;
            }
            Object other = otherValue(name, type);
            String key = type == List.class ? name + "[0]" : name;
            Object bound = value(name, Map.of(key, other.toString()));
            assertThat(String.valueOf(bound)).as(name)
                    .isNotEqualTo(String.valueOf(value(name, Map.of())))
                    .isEqualTo(String.valueOf(expected(name, type, other)));
        });
    }

    @Test
    void documentedDefaultsAreTheRealOnes() {
        documentedDefaults.forEach((name, cell) -> {
            Matcher code = CODE.matcher(cell);
            if (code.matches()) {
                assertThat(value(name, Map.of())).as(name)
                        .isNotNull()
                        .isEqualTo(value(name, Map.of(name, code.group(1))));
            } else if (cell.equals("required")) {
                assertThatThrownBy(() -> bindSource(Map.of(), name)).as(name)
                        .hasRootCauseInstanceOf(IllegalArgumentException.class);
            } else {
                assertThat(cell).as(name).isEqualTo("none");
                assertThat(value(name, Map.of())).as(name).isEqualTo(List.of());
            }
        });
    }

    private static Map<String, Class<?>> properties() {
        Map<String, Class<?>> result = new LinkedHashMap<>();
        collect("rag-engine.", RagEngineProperties.class, result);
        collect("rag-engine.web.", RagWebProperties.class, result);
        return result;
    }

    private static void collect(String prefix, Class<?> record, Map<String, Class<?>> result) {
        for (RecordComponent component : record.getRecordComponents()) {
            String name = prefix + kebab(component.getName());
            Class<?> type = component.getType();
            if (type.isRecord()) {
                collect(name + ".", type, result);
            } else if (component.getGenericType() instanceof ParameterizedType list
                    && list.getActualTypeArguments()[0] instanceof Class<?> item && item.isRecord()) {
                collect(name + "[].", item, result);
            } else {
                result.put(name, type);
            }
        }
    }

    private static String kebab(String name) {
        return name.replaceAll("([A-Z])", "-$1").toLowerCase();
    }

    private static Object otherValue(String name, Class<?> type) {
        if (type == boolean.class) {
            return !(Boolean) value(name, Map.of());
        }
        if (type == int.class) {
            // one step in the direction that keeps top-k at or below max-top-k
            int current = (Integer) value(name, Map.of());
            return name.endsWith(".top-k") ? current - 1 : current + 1;
        }
        if (type == double.class) {
            return 0.5;
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            Object current = value(name, Map.of());
            return constants[0].equals(current) ? constants[1] : constants[0];
        }
        if (type == String.class || type == List.class) {
            return "/other";
        }
        return "1KB";
    }

    private static Object expected(String name, Class<?> type, Object other) {
        if (type == List.class) {
            return List.of(other);
        }
        return type == String.class || type.isEnum() || type.isPrimitive() ? other
                : DataSize.parse(other.toString());
    }

    private static Object value(String name, Map<String, String> values) {
        Object current;
        String path;
        if (name.startsWith(SOURCE_PREFIX)) {
            Map<String, String> source = new HashMap<>(Map.of(
                    SOURCE_PREFIX + "name", "docs", SOURCE_PREFIX + "path", "docs"));
            source.putAll(values);
            current = bindSource(source, null);
            path = name.substring(SOURCE_PREFIX.length());
        } else if (name.startsWith("rag-engine.web.")) {
            current = binder(values).bindOrCreate("rag-engine.web", RagWebProperties.class);
            path = name.substring("rag-engine.web.".length());
        } else {
            current = binder(values).bindOrCreate("rag-engine", RagEngineProperties.class);
            path = name.substring("rag-engine.".length());
        }
        for (String part : path.split("\\.")) {
            current = component(current, part);
        }
        return current;
    }

    private static RagEngineProperties.Source bindSource(Map<String, String> values, String missing) {
        Map<String, String> source = new HashMap<>(values);
        if (missing != null) {
            source.put(SOURCE_PREFIX + "name", "docs");
            source.put(SOURCE_PREFIX + "path", "docs");
            source.remove(missing);
        }
        Map<String, String> indexed = new HashMap<>();
        source.forEach((key, value) -> indexed.put(key.replace("sources[]", "sources[0]"), value));
        return binder(indexed).bindOrCreate("rag-engine", RagEngineProperties.class).sources().get(0);
    }

    private static Binder binder(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values));
    }

    private static Object component(Object record, String property) {
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            if (kebab(component.getName()).equals(property)) {
                try {
                    return component.getAccessor().invoke(record);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        throw new AssertionError("No property " + property);
    }
}
