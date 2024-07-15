/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.sql.init.dependency;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link DatabaseInitializationDependencyConfigurer}.
 *
 * @author Andy Wilkinson
 */
class DatabaseInitializationDependencyConfigurerTests {

	private final ConfigurableEnvironment environment = new MockEnvironment();

	DatabaseInitializerDetector databaseInitializerDetector = MockedDatabaseInitializerDetector.mock;

	DependsOnDatabaseInitializationDetector dependsOnDatabaseInitializationDetector = MockedDependsOnDatabaseInitializationDetector.mock;

	@TempDir
	File temp;

	@BeforeEach
	void resetMocks() {
		reset(MockedDatabaseInitializerDetector.mock, MockedDependsOnDatabaseInitializationDetector.mock);
	}

	@Test
	void whenDetectorsAreCreatedThenTheEnvironmentCanBeInjected() {
		performDetection(Arrays.asList(ConstructorInjectionDatabaseInitializerDetector.class,
				ConstructorInjectionDependsOnDatabaseInitializationDetector.class), (context) -> {
					BeanDefinition alpha = BeanDefinitionBuilder.genericBeanDefinition(String.class)
							.getBeanDefinition();
					context.registerBeanDefinition("alpha", alpha);
					context.refresh();
					assertThat(ConstructorInjectionDatabaseInitializerDetector.environment).isEqualTo(this.environment);
					assertThat(ConstructorInjectionDependsOnDatabaseInitializationDetector.environment)
							.isEqualTo(this.environment);
				});
	}

	@Test
	void whenDependenciesAreConfiguredThenBeansThatDependUponDatabaseInitializationDependUponDetectedDatabaseInitializers() {
		BeanDefinition alpha = BeanDefinitionBuilder.genericBeanDefinition(String.class).getBeanDefinition();
		BeanDefinition bravo = BeanDefinitionBuilder.genericBeanDefinition(String.class).getBeanDefinition();
		performDetection(Arrays.asList(MockedDatabaseInitializerDetector.class,
				MockedDependsOnDatabaseInitializationDetector.class), (context) -> {
					context.registerBeanDefinition("alpha", alpha);
					context.registerBeanDefinition("bravo", bravo);
					given(this.databaseInitializerDetector.detect(context.getBeanFactory()))
							.willReturn(Collections.singleton("alpha"));
					given(this.dependsOnDatabaseInitializationDetector.detect(context.getBeanFactory()))
							.willReturn(Collections.singleton("bravo"));
					context.refresh();
					assertThat(alpha.getAttribute(DatabaseInitializerDetector.class.getName()))
							.isEqualTo(MockedDatabaseInitializerDetector.class.getName());
					assertThat(bravo.getAttribute(DatabaseInitializerDetector.class.getName())).isNull();
					verify(this.databaseInitializerDetector).detectionComplete(context.getBeanFactory(),
							Collections.singleton("alpha"));
					assertThat(bravo.getDependsOn()).containsExactly("alpha");
				});
	}

	private void performDetection(Collection<Class<?>> detectors,
			Consumer<AnnotationConfigApplicationContext> contextCallback) {
		DetectorSpringFactoriesClassLoader detectorSpringFactories = new DetectorSpringFactoriesClassLoader(this.temp);
		detectors.forEach(detectorSpringFactories::register);
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.setEnvironment(this.environment);
			context.setClassLoader(detectorSpringFactories);
			context.register(DependencyConfigurerConfiguration.class);
			contextCallback.accept(context);
		}
	}

	@Configuration(proxyBeanMethods = false)
	@Import(DatabaseInitializationDependencyConfigurer.class)
	static class DependencyConfigurerConfiguration {

	}

	static class ConstructorInjectionDatabaseInitializerDetector implements DatabaseInitializerDetector {

		private static Environment environment;

		ConstructorInjectionDatabaseInitializerDetector(Environment environment) {
			ConstructorInjectionDatabaseInitializerDetector.environment = environment;
		}

		@Override
		public Set<String> detect(ConfigurableListableBeanFactory beanFactory) {
			return Collections.singleton("alpha");
		}

	}

	static class ConstructorInjectionDependsOnDatabaseInitializationDetector
			implements DependsOnDatabaseInitializationDetector {

		private static Environment environment;

		ConstructorInjectionDependsOnDatabaseInitializationDetector(Environment environment) {
			ConstructorInjectionDependsOnDatabaseInitializationDetector.environment = environment;
		}

		@Override
		public Set<String> detect(ConfigurableListableBeanFactory beanFactory) {
			return Collections.emptySet();
		}

	}

	static class MockedDatabaseInitializerDetector implements DatabaseInitializerDetector {

		private static DatabaseInitializerDetector mock = Mockito.mock(DatabaseInitializerDetector.class);

		@Override
		public Set<String> detect(ConfigurableListableBeanFactory beanFactory) {
			return MockedDatabaseInitializerDetector.mock.detect(beanFactory);
		}

		@Override
		public void detectionComplete(ConfigurableListableBeanFactory beanFactory,
				Set<String> databaseInitializerNames) {
			mock.detectionComplete(beanFactory, databaseInitializerNames);
		}

	}

	static class MockedDependsOnDatabaseInitializationDetector implements DependsOnDatabaseInitializationDetector {

		private static DependsOnDatabaseInitializationDetector mock = Mockito
				.mock(DependsOnDatabaseInitializationDetector.class);

		@Override
		public Set<String> detect(ConfigurableListableBeanFactory beanFactory) {
			return MockedDependsOnDatabaseInitializationDetector.mock.detect(beanFactory);
		}

	}

	static class DetectorSpringFactoriesClassLoader extends ClassLoader {

		private final Set<Class<DatabaseInitializerDetector>> databaseInitializerDetectors = new HashSet<>();

		private final Set<Class<DependsOnDatabaseInitializationDetector>> dependsOnDatabaseInitializationDetectors = new HashSet<>();

		private final File temp;

		DetectorSpringFactoriesClassLoader(File temp) {
			this.temp = temp;
		}

		@SuppressWarnings("unchecked")
		void register(Class<?> detector) {
			if (DatabaseInitializerDetector.class.isAssignableFrom(detector)) {
				this.databaseInitializerDetectors.add((Class<DatabaseInitializerDetector>) detector);
			}
			else if (DependsOnDatabaseInitializationDetector.class.isAssignableFrom(detector)) {
				this.dependsOnDatabaseInitializationDetectors
						.add((Class<DependsOnDatabaseInitializationDetector>) detector);
			}
			else {
				throw new IllegalArgumentException("Unsupported detector type '" + detector.getName() + "'");
			}
		}

		@Override
		public Enumeration<URL> getResources(String name) throws IOException {
			if (!"META-INF/spring.factories".equals(name)) {
				return super.findResources(name);
			}
			Properties properties = new Properties();
			properties.put(DatabaseInitializerDetector.class.getName(), String.join(",",
					this.databaseInitializerDetectors.stream().map(Class::getName).collect(Collectors.toList())));
			properties.put(DependsOnDatabaseInitializationDetector.class.getName(),
					String.join(",", this.dependsOnDatabaseInitializationDetectors.stream().map(Class::getName)
							.collect(Collectors.toList())));
			File springFactories = new File(this.temp, "spring.factories");
			try (FileWriter writer = new FileWriter(springFactories)) {
				properties.store(writer, "");
			}
			return Collections.enumeration(Collections.singleton(springFactories.toURI().toURL()));
		}

	}

}
