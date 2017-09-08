/*
 * Created on 2004-12-01
 *
 */
package org.hibernate.tool.hbm2x.JdbcHbm2JavaEjb3;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.Persistence;

import org.hibernate.tool.hbm2x.POJOExporter;
import org.hibernate.tool.metadata.MetadataSourcesFactory;
import org.hibernate.tools.test.util.FileUtil;
import org.hibernate.tools.test.util.JUnitUtil;
import org.hibernate.tools.test.util.JavaUtil;
import org.hibernate.tools.test.util.JdbcUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * @author max
 * @author koen
 *
 */
public class TestCase {
	
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();
	
	private File outputDir = null;

	@Before
	public void setUp() {
		JdbcUtil.createDatabase(this);
		outputDir = temporaryFolder.getRoot();
		POJOExporter exporter = new POJOExporter();
		exporter.setMetadataSources(MetadataSourcesFactory
				.createJdbcSources(null, null, true));
		exporter.setOutputDirectory(outputDir);
		exporter.setTemplatePath(new String[0]);
		exporter.getProperties().setProperty("ejb3", "true");
		exporter.getProperties().setProperty("jdk5", "true");
		exporter.start();
	}
	
	@After
	public void tearDown() {
		JdbcUtil.dropDatabase(this);
	}
	
	@Test
	public void testFileExistence() {
		JUnitUtil.assertIsNonEmptyFile( new File(outputDir.getAbsolutePath() + "/Master.java") );
	}

	@Test
	public void testUniqueConstraints() {
		Assert.assertEquals(null, FileUtil.findFirstString( "uniqueConstraints", new File(outputDir,"Master.java") ));
		Assert.assertNotNull(FileUtil.findFirstString( "uniqueConstraints", new File(outputDir,"Uniquemaster.java") ));
	}
	
	@Test
	public void testCompile() {
		File destination = new File(temporaryFolder.getRoot(), "destination");
		destination.mkdir();
		List<String> jars = new ArrayList<String>();
		jars.add(JavaUtil.resolvePathToJarFileFor(Persistence.class)); // for jpa api
		JavaUtil.compile(outputDir, destination, jars);
		JUnitUtil.assertIsNonEmptyFile(new File(destination, "Master.class"));
		JUnitUtil.assertIsNonEmptyFile(new File(destination, "Uniquemaster.class"));
	}
	
}
