/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.modules;

import java.lang.instrument.ClassFileTransformer;
import java.security.AllPermission;
import java.security.PermissionCollection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@code Module} specification which is used by a {@code ModuleLoader} to define new modules.
 *
 * @apiviz.exclude
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class ModuleSpec {

    private final String name;

    ModuleSpec(final String name) {
        this.name = name;
    }

    /**
     * Get a builder for a new module specification.
     *
     * @param moduleIdentifier the module identifier
     * @return the builder
     * @deprecated Use {@link #build(String)} instead.
     */
    @Deprecated
    public static Builder build(final ModuleIdentifier moduleIdentifier) {
        return build(moduleIdentifier.toString());
    }

    /**
     * Get a builder for a new module specification.
     *
     * @param name the module name
     * @return the builder
     */
    public static Builder build(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        return new Builder() {
            private String mainClass;
            private AssertionSetting assertionSetting = AssertionSetting.INHERIT;
            private final List<ResourceLoaderSpec> resourceLoaders = new ArrayList<ResourceLoaderSpec>(0);
            private final List<DependencySpec> dependencies = new ArrayList<DependencySpec>();
            private final Map<String, String> properties = new LinkedHashMap<String, String>(0);
            private LocalLoader fallbackLoader;
            private ModuleClassLoaderFactory moduleClassLoaderFactory;
            private ClassFileTransformer classFileTransformer;
            private PermissionCollection permissionCollection;
            private Version version;

            @Override
            public Builder setFallbackLoader(final LocalLoader fallbackLoader) {
                this.fallbackLoader = fallbackLoader;
                return this;
            }

            @Override
            public Builder setMainClass(final String mainClass) {
                this.mainClass = mainClass;
                return this;
            }

            @Override
            public Builder setAssertionSetting(final AssertionSetting assertionSetting) {
                this.assertionSetting = assertionSetting == null ? AssertionSetting.INHERIT : assertionSetting;
                return this;
            }

            @Override
            public Builder addDependency(final DependencySpec dependencySpec) {
                dependencies.add(dependencySpec);
                return this;
            }

            @Override
            public Builder addResourceRoot(final ResourceLoaderSpec resourceLoader) {
                resourceLoaders.add(resourceLoader);
                return this;
            }

            @Override
            public Builder setModuleClassLoaderFactory(final ModuleClassLoaderFactory moduleClassLoaderFactory) {
                this.moduleClassLoaderFactory = moduleClassLoaderFactory;
                return this;
            }

            @Override
            public Builder setClassFileTransformer(final ClassFileTransformer classFileTransformer) {
                this.classFileTransformer = classFileTransformer;
                return this;
            }

            @Override
            public Builder addProperty(final String name, final String value) {
                properties.put(name, value);
                return this;
            }

            @Override
            public Builder setPermissionCollection(PermissionCollection permissionCollection) {
                this.permissionCollection = permissionCollection;
                return this;
            }

            @Override
            public Builder setVersion(Version version) {
                this.version = version;
                return this;
            }

            @Override
            public ModuleSpec create() {
                return new ConcreteModuleSpec(name, mainClass, assertionSetting, resourceLoaders.toArray(new ResourceLoaderSpec[resourceLoaders.size()]), dependencies.toArray(new DependencySpec[dependencies.size()]), fallbackLoader, moduleClassLoaderFactory, classFileTransformer, properties, permissionCollection, version);
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }

    /**
     * Get a builder for a new module alias specification.
     *
     * @param moduleIdentifier the module identifier
     * @param aliasTarget the alias target identifier
     * @return the builder
     * @deprecated Use {@link #buildAlias(String, String)} instead.
     */
    @Deprecated
    public static AliasBuilder buildAlias(final ModuleIdentifier moduleIdentifier, final ModuleIdentifier aliasTarget) {
        return buildAlias(moduleIdentifier.toString(), aliasTarget.toString());
    }

    /**
     * Get a builder for a new module alias specification.
     *
     * @param name the module name
     * @param aliasName the alias target name
     * @return the builder
     */
    public static AliasBuilder buildAlias(final String name, final String aliasName) {
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        if (aliasName == null) {
            throw new IllegalArgumentException("aliasName is null");
        }
        return new AliasBuilder() {
            public ModuleSpec create() {
                return new AliasModuleSpec(name, aliasName);
            }

            public String getName() {
                return null;
            }

            public String getAliasName() {
                return null;
            }
        };
    }

    /**
     * Get the module identifier for the module which is specified by this object.
     *
     * @return the module identifier
     * @deprecated Use {@link #getName()} instead.
     */
    public ModuleIdentifier getModuleIdentifier() {
        return ModuleIdentifier.fromString(name);
    }

    /**
     * Get the module name for the module which is specified by this object.
     *
     * @return the module name
     */
    public String getName() {
        return name;
    }

    /**
     * A builder for new concrete module specifications.
     *
     * @apiviz.exclude
     */
    public interface Builder {

        /**
         * Set the main class for this module, or {@code null} for none.
         *
         * @param mainClass the main class name
         * @return this builder
         */
        ModuleSpec.Builder setMainClass(String mainClass);

        /**
         * Set the default assertion setting for this module.
         *
         * @param assertionSetting the assertion setting
         * @return this builder
         */
        ModuleSpec.Builder setAssertionSetting(AssertionSetting assertionSetting);

        /**
         * Add a dependency specification.
         *
         * @param dependencySpec the dependency specification
         * @return this builder
         */
        ModuleSpec.Builder addDependency(DependencySpec dependencySpec);

        /**
         * Add a local resource root, from which this module will load class definitions and resources.
         *
         * @param resourceLoader the resource loader for the root
         * @return this builder
         */
        ModuleSpec.Builder addResourceRoot(ResourceLoaderSpec resourceLoader);

        /**
         * Create the module specification from this builder.
         *
         * @return the module specification
         */
        ModuleSpec create();

        /**
         * Get the identifier of the module being defined by this builder.
         *
         * @return the module identifier
         * @deprecated use {@link #getName()} instead
         */
        @Deprecated
        default ModuleIdentifier getIdentifier() {
            return ModuleIdentifier.fromString(getName());
        }

        /**
         * Get the name of the module being defined by this builder.
         *
         * @return the module name
         */
        String getName();

        /**
         * Sets a "fall-back" loader that will attempt to load a class if all other mechanisms
         * are unsuccessful.
         *
         * @param fallbackLoader the fall-back loader
         * @return this builder
         */
        ModuleSpec.Builder setFallbackLoader(final LocalLoader fallbackLoader);

        /**
         * Set the module class loader factory to use to create the module class loader for this module.
         *
         * @param moduleClassLoaderFactory the factory
         * @return this builder
         */
        ModuleSpec.Builder setModuleClassLoaderFactory(ModuleClassLoaderFactory moduleClassLoaderFactory);

        /**
         * Set the class file transformer to use for this module.
         *
         * @param classFileTransformer the class file transformer
         * @return this builder
         */
        ModuleSpec.Builder setClassFileTransformer(ClassFileTransformer classFileTransformer);

        /**
         * Add a property to this module specification.
         *
         * @param name the property name
         * @param value the property value
         * @return this builder
         */
        ModuleSpec.Builder addProperty(String name, String value);

        /**
         * Set the permission collection for this module specification.  If none is given, a collection implying
         * {@link AllPermission} is assumed.
         *
         * @param permissionCollection the permission collection
         * @return this builder
         */
        ModuleSpec.Builder setPermissionCollection(PermissionCollection permissionCollection);

        /**
         * Set the version for this module specification, or {@code null} to set no version for this module.
         *
         * @param version the module version
         * @return this builder
         */
        ModuleSpec.Builder setVersion(Version version);
    }

    /**
     * A builder for new alias module specifications.
     */
    public interface AliasBuilder {

        /**
         * Create the module specification from this builder.
         *
         * @return the module specification
         */
        ModuleSpec create();

        /**
         * Get the identifier of the module being defined by this builder.
         *
         * @return the module identifier
         * @deprecated Use {@link #getName()} instead.
         */
        @Deprecated
        default ModuleIdentifier getIdentifier() {
            return ModuleIdentifier.fromString(getName());
        }

        /**
         * Get the name of the module being defined by this builder.
         *
         * @return the module name
         */
        String getName();

        /**
         * Get the identifier of the module being referenced by this builder.
         *
         * @return the module identifier
         * @deprecated Use {@link #getAliasName()} instead.
         */
        @Deprecated
        default ModuleIdentifier getAliasTarget() {
            return ModuleIdentifier.fromString(getAliasName());
        }

        /**
         * Get the name of the module being referenced by this builder.
         *
         * @return the module name
         */
        String getAliasName();
    }
}
