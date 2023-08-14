/*
 *
 * Copyright 2023 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.ibm.eventstreams.connect.jdbcsink.database.writer;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.jupiter.api.Test;

import com.ibm.db2.jcc.am.DatabaseMetaData;
import com.ibm.eventstreams.connect.jdbcsink.database.datasource.IDataSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class JDBCWriterTest {

    @Test
    public void testCreateTable_TableDoesNotExist_CreatesTable() throws SQLException {
        // Prepare test data
        final String postgresCreateTableStatement = "CREATE TABLE test_table (id SERIAL PRIMARY KEY, id INTEGER NOT NULL, name VARCHAR(255) NOT NULL)";
        final String db2CreateTableStatement = "CREATE TABLE test_table (id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, id INTEGER NOT NULL, name VARCHAR(255) NOT NULL)";
        final Map<String, String> createTableStatements = new HashMap<>();
        createTableStatements.put("POSTGRESQL", postgresCreateTableStatement);
        createTableStatements.put("DB2", db2CreateTableStatement);

        final String tableName = "test_table";
        final Schema schema = SchemaBuilder.struct()
                .field("id", Schema.INT32_SCHEMA)
                .field("name", Schema.STRING_SCHEMA)
                .build();

        for (final String database : Arrays.asList("POSTGRESQL", "DB2")) {
            final Connection connection = mock(Connection.class);
            final IDataSource dataSource = mock(IDataSource.class);
            final DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
            final PreparedStatement preparedStatement = mock(PreparedStatement.class);
            when(dataSource.getConnection()).thenReturn(connection);
            final JDBCWriter jdbcWriter = new JDBCWriter(dataSource);
            when(connection.prepareStatement(createTableStatements.get(database)))
                    .thenReturn(preparedStatement);
            when(connection.getMetaData()).thenReturn(databaseMetaData);
            when(databaseMetaData.getDatabaseProductName()).thenReturn(database);

            // Execute the method under test
            jdbcWriter.createTable(tableName, schema);
            // Verify the behavior
            verify(connection).prepareStatement(createTableStatements.get(database));
            verify(preparedStatement).execute();
            verify(preparedStatement).close();
        }
    }

    @Test
    public void testInsert_RecordsExist_InsertsRecords() throws SQLException {
        // Prepare test data
        final String postgresInsertStatement = "INSERT INTO schema.test_table(id, name) VALUES (?, ?)";
        final String db2InsertStatement = "INSERT INTO schema.test_table(id, name) VALUES (?, ?)";
        final Map<String, String> insertStatements = new HashMap<>();
        insertStatements.put("POSTGRESQL", postgresInsertStatement);
        insertStatements.put("DB2", db2InsertStatement);

        final String tableName = "schema.test_table";
        final Schema schema = SchemaBuilder.struct()
                .field("id", Schema.INT32_SCHEMA)
                .field("name", Schema.STRING_SCHEMA)
                .build();
        final Struct recordValue = new Struct(schema)
                .put("id", 1)
                .put("name", "John");
        final SinkRecord record = new SinkRecord("topic", 0, null, null, schema, recordValue, 0);
        final Collection<SinkRecord> records = Collections.singletonList(record);

        for (final String database : Arrays.asList("POSTGRESQL", "DB2")) {
            final Connection connection = mock(Connection.class);
            final IDataSource dataSource = spy(IDataSource.class);
            final DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
            final PreparedStatement preparedStatement = mock(PreparedStatement.class);
            final ResultSet resultSet = mock(ResultSet.class);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(databaseMetaData);
            when(databaseMetaData.getTables(any(), any(), any(), any())).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(connection.prepareStatement(insertStatements.get(database)))
                    .thenReturn(preparedStatement);
            final JDBCWriter jdbcWriter = new JDBCWriter(dataSource);

            // Execute the method under test
            jdbcWriter.insert(tableName, records);
            // Verify the behavior
            verify(connection).prepareStatement(insertStatements.get(database));
            verify(preparedStatement).setObject(1, 1);
            verify(preparedStatement).setObject(2, "John");
            verify(preparedStatement).addBatch();
            verify(preparedStatement).executeBatch();
            verify(preparedStatement).close();
            verify(connection).close();
        }
    }

    @Test
    public void testInsert_RecordsExist_CreateAndInsertsRecords() throws SQLException {
        // Prepare test data
        final String postgresCreateTableStatement = "CREATE TABLE schema.accounts (id SERIAL PRIMARY KEY, user_id INTEGER NOT NULL, username VARCHAR(255) NOT NULL, password VARCHAR(255) NOT NULL, email VARCHAR(255), created_on BIGINT NOT NULL, last_login BIGINT NOT NULL, is_admin BOOLEAN NOT NULL, is_staff BOOLEAN NOT NULL, is_active BOOLEAN NOT NULL)";
        final String db2CreateTableStatement = "CREATE TABLE schema.accounts (id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, user_id INTEGER NOT NULL, username VARCHAR(255) NOT NULL, password VARCHAR(255) NOT NULL, email VARCHAR(255), created_on BIGINT NOT NULL, last_login BIGINT NOT NULL, is_admin BOOLEAN NOT NULL, is_staff BOOLEAN NOT NULL, is_active BOOLEAN NOT NULL)";
        final Map<String, String> createTableStatements = new HashMap<>();
        createTableStatements.put("POSTGRESQL", postgresCreateTableStatement);
        createTableStatements.put("DB2", db2CreateTableStatement);

        final String postgresInsertStatement = "INSERT INTO schema.accounts(user_id, username, password, email, created_on, last_login, is_admin, is_staff, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        final String db2InsertStatement = "INSERT INTO schema.accounts(user_id, username, password, email, created_on, last_login, is_admin, is_staff, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        final Map<String, String> insertStatements = new HashMap<>();
        insertStatements.put("POSTGRESQL", postgresInsertStatement);
        insertStatements.put("DB2", db2InsertStatement);

        final String tableName = "schema.accounts";
        final Schema schema = SchemaBuilder.struct()
                .field("user_id", Schema.INT32_SCHEMA)
                .field("username", Schema.STRING_SCHEMA)
                .field("password", Schema.STRING_SCHEMA)
                .field("email", Schema.OPTIONAL_STRING_SCHEMA)
                .field("created_on", Schema.INT64_SCHEMA)
                .field("last_login", Schema.INT64_SCHEMA)
                .field("is_admin", Schema.BOOLEAN_SCHEMA)
                .field("is_staff", Schema.BOOLEAN_SCHEMA)
                .field("is_active", Schema.BOOLEAN_SCHEMA)
                .build();

        final Struct johnStruct = new Struct(schema)
                .put("user_id", 1)
                .put("username", "John")
                .put("password", "JohnPassword")
                .put("email", "john@somerandommail.com")
                .put("created_on", 1234567890L)
                .put("last_login", 1234567891L)
                .put("is_admin", true)
                .put("is_staff", false)
                .put("is_active", true);
        final SinkRecord johnRecord = new SinkRecord("topic", 0, null, null, schema, johnStruct, 0);

        final Struct janeStruct = new Struct(schema)
                .put("user_id", 2)
                .put("username", "Jane")
                .put("password", "JanePassword")
                .put("created_on", 1234567892L)
                .put("last_login", 1234567893L)
                .put("is_admin", false)
                .put("is_staff", true)
                .put("is_active", false);
        final SinkRecord janeRecord = new SinkRecord("topic", 0, null, null, schema, janeStruct, 0);
        final Collection<SinkRecord> records = Arrays.asList(johnRecord, janeRecord);

        for (final String database : Arrays.asList("POSTGRESQL", "DB2")) {
            // Prepare mocks
            final Connection connection = mock(Connection.class);
            final IDataSource dataSource = mock(IDataSource.class);
            final DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
            final PreparedStatement createPreparedStatement = mock(PreparedStatement.class);
            final PreparedStatement insertPreparedStatement = mock(PreparedStatement.class);
            final ResultSet resultSet = mock(ResultSet.class);
            when(dataSource.getConnection()).thenReturn(connection);
            final JDBCWriter jdbcWriter = new JDBCWriter(dataSource);
            when(connection.getMetaData()).thenReturn(databaseMetaData);
            when(databaseMetaData.getTables(any(), any(), any(), any())).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);
            when(databaseMetaData.getDatabaseProductName()).thenReturn(database);
            when(connection.prepareStatement(createTableStatements.get(database)))
                    .thenReturn(createPreparedStatement);
            when(connection.prepareStatement(insertStatements.get(database)))
                    .thenReturn(insertPreparedStatement);

            // Execute the method under test
            jdbcWriter.insert(tableName, records);
            // Verify the behavior
            verify(connection).prepareStatement(createTableStatements.get(database));
            verify(createPreparedStatement).execute();
            verify(createPreparedStatement).close();
            verify(connection).prepareStatement(insertStatements.get(database));
            verify(insertPreparedStatement).setObject(1, 1);
            verify(insertPreparedStatement).setObject(2, "John");
            verify(insertPreparedStatement).setObject(3, "JohnPassword");
            verify(insertPreparedStatement).setObject(4, "john@somerandommail.com");
            verify(insertPreparedStatement).setObject(5, 1234567890L);
            verify(insertPreparedStatement).setObject(6, 1234567891L);
            verify(insertPreparedStatement).setObject(7, true);
            verify(insertPreparedStatement).setObject(8, false);
            verify(insertPreparedStatement).setObject(9, true);
            verify(insertPreparedStatement).setObject(1, 2);
            verify(insertPreparedStatement).setObject(2, "Jane");
            verify(insertPreparedStatement).setObject(3, "JanePassword");
            verify(insertPreparedStatement).setObject(4, null);
            verify(insertPreparedStatement).setObject(5, 1234567892L);
            verify(insertPreparedStatement).setObject(6, 1234567893L);
            verify(insertPreparedStatement).setObject(7, false);
            verify(insertPreparedStatement).setObject(8, true);
            verify(insertPreparedStatement).setObject(9, false);
            verify(insertPreparedStatement, times(2)).addBatch();
            verify(insertPreparedStatement).executeBatch();
            verify(insertPreparedStatement).close();
            verify(connection).close();
        }
    }
}
