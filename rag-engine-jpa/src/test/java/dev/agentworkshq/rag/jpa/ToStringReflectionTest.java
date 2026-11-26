package dev.agentworkshq.rag.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agentworkshq.rag.jpa.entity.IngestionJob;
import dev.agentworkshq.rag.jpa.entity.SourceDocument;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ToStringReflectionTest {

    private static final String PLAIN_CLASS =
            "a class, not a record: it keeps Object.toString, which prints only the class name and a hash";

    // every entry must keep Object.toString, a class that starts to override it has to be reviewed
    private static final Map<Class<?>, String> SAFE = Map.ofEntries(
            Map.entry(IngestionJob.class, PLAIN_CLASS),
            Map.entry(SourceDocument.class, PLAIN_CLASS));

    @Test
    void everyPublicTypeThatCanHoldTextDeclaresItsOwnToString() throws Exception {
        List<Class<?>> types = publicTypes();

        assertThat(types).hasSizeGreaterThanOrEqualTo(4).contains(IngestionJob.class);
        assertThat(types.stream().filter(t -> holdsText(t) && !declaresToString(t) && !SAFE.containsKey(t)))
                .as("types that need a toString which prints no free text")
                .isEmpty();
    }

    @Test
    void theAllowListHasNoStaleOrUnsafeEntries() throws Exception {
        for (Class<?> type : SAFE.keySet()) {
            assertThat(holdsText(type) && !declaresToString(type))
                    .as("%s no longer needs an entry", type.getSimpleName()).isTrue();
            assertThat(type.getMethod("toString").getDeclaringClass()).as(type.getSimpleName()).isEqualTo(Object.class);
        }
    }

    @Test
    void theCheckTellsGeneratedAndDeclaredToStringApart() {
        assertThat(holdsText(Generated.class)).isTrue();
        assertThat(declaresToString(Generated.class)).isFalse();
        assertThat(declaresToString(Declared.class)).isTrue();
        assertThat(holdsText(Nested.class)).isTrue();
        assertThat(holdsText(Listed.class)).isTrue();
        assertThat(holdsText(Numbers.class)).isFalse();
    }

    record Generated(String text) {
    }

    record Declared(String text) {

        @Override
        public String toString() {
            return "Declared[]";
        }
    }

    record Nested(Generated inner) {
    }

    record Listed(List<String> values) {
    }

    record Numbers(int count, Duration time, List<Integer> values) {
    }

    private static List<Class<?>> publicTypes() throws IOException, URISyntaxException, ClassNotFoundException {
        Path root = Path.of(IngestionJob.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertThat(root).as("the main classes must be a directory, run the test with Maven").isDirectory();
        List<String> names;
        try (Stream<Path> files = Files.walk(root)) {
            names = files.map(p -> root.relativize(p).toString())
                    .filter(n -> n.endsWith(".class"))
                    .map(n -> n.substring(0, n.length() - ".class".length()).replace('\\', '.').replace('/', '.'))
                    .sorted()
                    .toList();
        }
        List<Class<?>> types = new ArrayList<>();
        for (String name : names) {
            Class<?> type = Class.forName(name, false, IngestionJob.class.getClassLoader());
            if (isPublic(type) && !type.isInterface() && !type.isEnum()) {
                types.add(type);
            }
        }
        return types;
    }

    private static boolean isPublic(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getEnclosingClass()) {
            if (!Modifier.isPublic(c.getModifiers())) {
                return false;
            }
        }
        return !type.isAnonymousClass() && !type.isLocalClass();
    }

    private static boolean holdsText(Class<?> type) {
        if (type.isRecord()) {
            return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getGenericType)
                    .anyMatch(ToStringReflectionTest::carriesText);
        }
        // fields of package private parents are included because the parent is not checked by itself
        for (Class<?> c = type; c != null && (c == type || !Modifier.isPublic(c.getModifiers())); c = c.getSuperclass()) {
            if (Arrays.stream(c.getDeclaredFields())
                    .filter(f -> !Modifier.isStatic(f.getModifiers()))
                    .map(Field::getGenericType)
                    .anyMatch(ToStringReflectionTest::carriesText)) {
                return true;
            }
        }
        return false;
    }

    private static boolean carriesText(Type type) {
        if (type instanceof ParameterizedType parameterized) {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            if (Map.class.isAssignableFrom(raw)) {
                return true;
            }
            return Collection.class.isAssignableFrom(raw)
                    && Arrays.stream(parameterized.getActualTypeArguments()).anyMatch(ToStringReflectionTest::carriesText);
        }
        if (type instanceof GenericArrayType array) {
            return carriesText(array.getGenericComponentType());
        }
        if (type instanceof Class<?> c) {
            if (c.isArray()) {
                return carriesText(c.getComponentType());
            }
            return c == Object.class || c == CharSequence.class || c == String.class || c.isRecord()
                    || Map.class.isAssignableFrom(c) || Collection.class.isAssignableFrom(c);
        }
        // wildcards and type variables could stand for anything
        return true;
    }

    // the toString that the compiler generates for a record is final, a written one is not
    private static boolean declaresToString(Class<?> type) {
        try {
            Method method = type.getDeclaredMethod("toString");
            return !type.isRecord() || !Modifier.isFinal(method.getModifiers());
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
