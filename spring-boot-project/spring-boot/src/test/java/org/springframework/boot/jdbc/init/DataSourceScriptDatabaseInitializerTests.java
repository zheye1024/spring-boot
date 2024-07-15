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

package org.springframework.boot.jdbc.init;

import java.util.UUID;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.sql.init.AbstractScriptDatabaseInitializer;
import org.springframework.boot.sql.init.AbstractScriptDatabaseInitializerTests;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tests for {@link DataSourceScriptDatabaseInitializer}.
 *
 * @author Andy Wilkinson
 */
class DataSourceScriptDatabaseInitializerTests extends AbstractScriptDatabaseInitializerTests {

	private final HikariDataSource dataSource = DataSourceBuilder.create().type(HikariDataSource.class)
			.url("jdbc:h2:mem:" + UUID.randomUUID()).build();

	@AfterEach
	void closeDataSource() {
		this.dataSource.close();
	}

	@Override
	protected AbstractScriptDatabaseInitializer createInitializer(DatabaseInitializationSettings settings) {
		return new DataSourceScriptDatabaseInitializer(this.dataSource, settings);
	}

	@Override
	protected int numberOfRows(String sql) {
		return new JdbcTemplate(this.dataSource).queryForObject(sql, Integer.class);
	}

}
