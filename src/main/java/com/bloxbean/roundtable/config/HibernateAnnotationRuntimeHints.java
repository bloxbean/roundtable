package com.bloxbean.roundtable.config;

import org.hibernate.annotations.DialectOverride;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;

/**
 * Registers Hibernate classes for reflection in GraalVM native image.
 *
 * Covers two cases:
 * 1. All annotation impl classes in hibernate-models (OrmAnnotationDescriptor$DynamicCreator
 *    reflectively calls their (ModelsContext) constructor).
 * 2. DialectOverride and its inner annotation types (DialectOverridesAnnotationHelper
 *    calls getDeclaredClasses() to build the override map at runtime).
 */
public class HibernateAnnotationRuntimeHints implements RuntimeHintsRegistrar {

    private static final String ANNOTATION_PACKAGE_PATTERN =
            "classpath:org/hibernate/boot/models/annotations/internal/*.class";

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        registerAnnotationImplClasses(hints, classLoader);
        registerDialectOverrideClasses(hints, classLoader);
    }

    private void registerAnnotationImplClasses(RuntimeHints hints, ClassLoader classLoader) {
        try {
            PathMatchingResourcePatternResolver resolver =
                    new PathMatchingResourcePatternResolver(classLoader);
            CachingMetadataReaderFactory readerFactory =
                    new CachingMetadataReaderFactory(classLoader);

            Resource[] resources = resolver.getResources(ANNOTATION_PACKAGE_PATTERN);
            for (Resource resource : resources) {
                String className = readerFactory.getMetadataReader(resource)
                        .getClassMetadata().getClassName();
                hints.reflection().registerTypeIfPresent(
                        classLoader,
                        className,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_PUBLIC_METHODS
                );
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to register Hibernate annotation runtime hints", e);
        }
    }

    private void registerDialectOverrideClasses(RuntimeHints hints, ClassLoader classLoader) {
        // Register DialectOverride itself with declared classes access so that
        // getDeclaredClasses() works in native image
        hints.reflection().registerType(DialectOverride.class,
                MemberCategory.DECLARED_CLASSES,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS
        );

        // Register every inner class (DialectOverride.SQLInsert, .SQLUpdate, etc.)
        // so their annotations (@OverridesAnnotation) are accessible via reflection
        for (Class<?> innerClass : DialectOverride.class.getDeclaredClasses()) {
            hints.reflection().registerType(innerClass,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.DECLARED_FIELDS
            );
        }
    }
}
