/*
 * Hibernate Tools, Tooling for your Hibernate Projects
 * 
 * Copyright 2004-2021 Red Hat, Inc.
 *
 * Licensed under the GNU Lesser General Public License (LGPL), 
 * version 2.1 or later (the "License").
 * You may not use this file except in compliance with the License.
 * You may read the licence in the 'lgpl.txt' file in the root folder of 
 * project or obtain a copy at
 *
 *     http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hibernate.tool.hbm2x.IncrementalSchemaReading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Environment;
import org.hibernate.mapping.Table;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.api.reveng.RevengDialect;
import org.hibernate.tool.api.reveng.RevengStrategy.SchemaSelection;
import org.hibernate.tool.api.reveng.TableIdentifier;
import org.hibernate.tool.internal.reveng.RevengMetadataCollector;
import org.hibernate.tool.internal.reveng.dialect.JDBCMetaDataDialect;
import org.hibernate.tool.internal.reveng.reader.DatabaseReader;
import org.hibernate.tool.internal.reveng.strategy.DefaultStrategy;
import org.hibernate.tool.internal.reveng.strategy.TableSelectorStrategy;
import org.hibernate.tools.test.util.JUnitUtil;
import org.hibernate.tools.test.util.JdbcUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * @author max
 * @author koen
 */
public class TestCase {
	
	private Properties properties = null;
	private String defaultSchema = null;
	private String defaultCatalog = null;
		
	public class MockedMetaDataDialect extends JDBCMetaDataDialect {
		List<String> gottenTables = new ArrayList<String>();
		public Iterator<Map<String, Object>> getTables(String catalog, String schema, String table) {
			gottenTables.add(table);
			return super.getTables( catalog, schema, table == null ? "%" : table );
		}	
		public Iterator<Map<String, Object>> getColumns(String catalog, String schema, String table, String column) {
			return super.getColumns(catalog, schema, table == null ? "%" : table, column == null ? "%" : column);
		}
	}	
	
	@BeforeEach
	public void setUp() {
		JdbcUtil.createDatabase(this);
		properties = Environment.getProperties();
		defaultSchema = properties.getProperty(AvailableSettings.DEFAULT_SCHEMA);
		defaultCatalog = properties.getProperty(AvailableSettings.DEFAULT_CATALOG);
	}
	
	@AfterEach
	public void tearDown() {
		JdbcUtil.dropDatabase(this);
	}
	
	//TODO HBX-2148: Reenable the tests
	@Disabled
	@Test
	public void testReadSchemaIncremental() {
		StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder();
		builder.applySettings(properties);
		ServiceRegistry serviceRegistry = builder.build();
		TableSelectorStrategy tss = new TableSelectorStrategy(new DefaultStrategy());
		MockedMetaDataDialect mockedMetaDataDialect = new MockedMetaDataDialect();
		DatabaseReader reader = DatabaseReader.create( properties, tss, mockedMetaDataDialect, serviceRegistry);
		
		tss.addSchemaSelection( createSchemaSelection(null,null, "CHILD") );
		
		RevengMetadataCollector dc = new RevengMetadataCollector();
		reader.readDatabaseSchema(dc);
		
		assertEquals(mockedMetaDataDialect.gottenTables.size(),1);
		assertEquals(mockedMetaDataDialect.gottenTables.get(0),"CHILD");
		
		Iterator<Table> iterator = dc.iterateTables();
		Table firstChild = iterator.next();
		assertEquals(firstChild.getName(), "CHILD");
		assertFalse(iterator.hasNext());
		
		assertFalse(firstChild.getForeignKeyIterator().hasNext(), "should not record foreignkey to table it doesn't know about yet");
		
		tss.clearSchemaSelections();
		tss.addSchemaSelection( createSchemaSelection(null, null, "MASTER") );
		
		mockedMetaDataDialect.gottenTables.clear();
		reader.readDatabaseSchema(dc);
		
		assertEquals(mockedMetaDataDialect.gottenTables.size(),1);
		assertEquals(mockedMetaDataDialect.gottenTables.get(0),"MASTER");
		
		
		iterator = dc.iterateTables();
		assertNotNull(iterator.next());
		assertNotNull(iterator.next());
		assertFalse(iterator.hasNext());
		
		Table table = getTable(dc, mockedMetaDataDialect, defaultCatalog, defaultSchema, "CHILD" );
		assertSame( firstChild, table );
		
		JUnitUtil.assertIteratorContainsExactly(
				"should have recorded one foreignkey to child table", 
				firstChild.getForeignKeyIterator(),
				1);		
		
		
		tss.clearSchemaSelections();		
		reader.readDatabaseSchema(dc);
		
		Table finalMaster = getTable(dc, mockedMetaDataDialect, defaultCatalog, defaultSchema, "MASTER" );
		
		assertSame(firstChild, getTable(dc, mockedMetaDataDialect, defaultCatalog, defaultSchema, "CHILD" ));
		JUnitUtil.assertIteratorContainsExactly(
				null,
				firstChild.getForeignKeyIterator(),
				1);
		JUnitUtil.assertIteratorContainsExactly(
				null,
				finalMaster.getForeignKeyIterator(),
				0);
	}

	private Table getTable(
			RevengMetadataCollector revengMetadataCollector, 
			RevengDialect metaDataDialect, 
			String catalog, 
			String schema, 
			String name) {
		return revengMetadataCollector.getTable(
				TableIdentifier.create(
						quote(metaDataDialect, catalog), 
						quote(metaDataDialect, schema), 
						quote(metaDataDialect, name)));
	}
 	
	private String quote(RevengDialect metaDataDialect, String name) {
		if (name == null)
			return name;
		if (metaDataDialect.needQuote(name)) {
			if (name.length() > 1 && name.charAt(0) == '`'
					&& name.charAt(name.length() - 1) == '`') {
				return name; // avoid double quoting
			}
			return "`" + name + "`";
		} else {
			return name;
		}
	}

	private SchemaSelection createSchemaSelection(String matchCatalog, String matchSchema, String matchTable) {
		return new SchemaSelection() {
			@Override
			public String getMatchCatalog() {
				return matchCatalog;
			}
			@Override
			public String getMatchSchema() {
				return matchSchema;
			}
			@Override
			public String getMatchTable() {
				return matchTable;
			}		
		};
	}
	
	
}
