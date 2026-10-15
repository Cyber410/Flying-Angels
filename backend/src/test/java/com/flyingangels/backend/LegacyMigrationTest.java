package com.flyingangels.backend;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;
class LegacyMigrationTest {
    @Test void upgradesLegacySchemaAndPreservesAssignmentsAndNotes() throws Exception {
        try(var mysql=new MySQLContainer<>("mysql:8.4").withDatabaseName("migration_test").withUsername("test").withPassword("test")) {
            mysql.start();
            Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).target("1").load().migrate();
            try(var connection=DriverManager.getConnection(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());var sql=connection.createStatement()) {
                sql.execute("ALTER TABLE event_results RENAME INDEX uk_legacy_result TO UK_generated_name");
                sql.execute("INSERT INTO athletes(id,first_name,last_name,note) VALUES(1,'Test','Athlete','Original note'),(2,'Pending','Athlete',NULL)");
                sql.execute("INSERT INTO meets(id,name,meet_date) VALUES(1,'Legacy meet','2026-05-16')");
                sql.execute("INSERT INTO event_categories(id,name,ranking_order) VALUES(1,'100m','ASC')");
                sql.execute("INSERT INTO events(id,name,event_date,meet_id,category_id,ranking_order) VALUES(1,'100m','2026-05-16',1,1,'ASC')");
                sql.execute("INSERT INTO event_results(event_id,category_id,athlete_id,result,score) VALUES(1,1,1,'12.4',12.4),(1,1,2,NULL,NULL)");
                // Simulate the original Hibernate-managed database without a migration history table.
                sql.execute("DROP TABLE flyway_schema_history");
                Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).baselineOnMigrate(true).baselineVersion("1").load().migrate();
                try(var rs=sql.executeQuery("SELECT COUNT(*) FROM event_participations")){rs.next();assertEquals(2,rs.getInt(1));}
                try(var rs=sql.executeQuery("SELECT COUNT(*) FROM event_results")){rs.next();assertEquals(1,rs.getInt(1));}
                sql.execute("INSERT INTO event_results(event_id,category_id,athlete_id,result,score) VALUES(1,1,1,'12.1',12.1)");
                try(var rs=sql.executeQuery("SELECT note FROM athletes WHERE id=1")){rs.next();assertEquals("Original note",rs.getString(1));}
                try(var rs=sql.executeQuery("SELECT start_date,end_date FROM meets WHERE id=1")){rs.next();assertEquals("2026-05-16",rs.getString(1));assertEquals(rs.getString(1),rs.getString(2));}
            }
        }
    }
}
