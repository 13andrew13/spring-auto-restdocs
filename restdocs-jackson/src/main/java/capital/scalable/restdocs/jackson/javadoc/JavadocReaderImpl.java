package capital.scalable.restdocs.jackson.javadoc;

import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.util.StringUtils.hasText;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

public class JavadocReaderImpl implements JavadocReader {
    private static final Logger log = getLogger(JavadocReader.class);

    private final Map<String, ClassJavadoc> classCache = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final File javadocJsonDir;

    public JavadocReaderImpl() {
        this(null);
    }

    public JavadocReaderImpl(String javadocJsonDir) {
        if (javadocJsonDir != null) {
            this.javadocJsonDir = new File(javadocJsonDir).getAbsoluteFile();
        } else {
            this.javadocJsonDir = systemPropertyJavadocJsonDir();
        }

        mapper.setVisibilityChecker(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
    }

    private ClassJavadoc getClass(Class<?> clazz) {
        String fileName = clazz.getCanonicalName() + ".json";

        ClassJavadoc classJavadoc = classCache.get(fileName);
        if (classJavadoc != null) {
            return classJavadoc;
        }

        try {
            File docSource = makeRelativeToConfiguredJavaDocJsonDir(new File(fileName));
            classJavadoc = mapper
                    .readerFor(ClassJavadoc.class)
                    .readValue(docSource);
        } catch (FileNotFoundException e) {
            log.warn("No JavaDoc found for {} at {}", clazz.getCanonicalName(), fileName);
            classJavadoc = new ClassJavadoc();
        } catch (IOException e) {
            log.error("Problem reading file {}", fileName, e);
            classJavadoc = new ClassJavadoc();
        }

        classCache.put(fileName, classJavadoc);
        return classJavadoc;
    }

    @Override
    public String resolveFieldComment(Class<?> javaBaseClass, String javaFieldName) {
        return getClass(javaBaseClass).getFieldComment(javaFieldName);
    }

    @Override
    public String resolveMethodComment(Class<?> javaBaseClass, String javaMethodName) {
        return getClass(javaBaseClass).getMethodComment(javaMethodName);
    }

    @Override
    public String resolveMethodParameterComment(Class<?> javaBaseClass, String javaMethodName,
            String javaParameterName) {
        return getClass(javaBaseClass).getMethodParameterComment(javaMethodName, javaParameterName);
    }

    private File makeRelativeToConfiguredJavaDocJsonDir(File outputFile) {
        if (javadocJsonDir != null) {
            return new File(javadocJsonDir, outputFile.getPath());
        }
        return new File(outputFile.getPath());
    }

    private File systemPropertyJavadocJsonDir() {
        String outputDir = System.getProperties().getProperty(
                "org.springframework.restdocs.javadocJsonDir");
        if (hasText(outputDir)) {
            return new File(outputDir).getAbsoluteFile();
        }
        return null;
    }
}