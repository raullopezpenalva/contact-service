package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.operator;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Explicit operator-only baseline. Unknown schemas are never adopted or repaired. */
public final class LegacySchemaBaseline {
    private LegacySchemaBaseline() {}

    public static void baseline(DataSource source) throws SQLException {
        try(Connection connection=source.getConnection()) {
            try(var lock=connection.createStatement()) {lock.execute("SELECT pg_advisory_lock(440042)");}
            try {
                if(!version(connection).startsWith("15.")) throw new IllegalStateException("Legacy baseline requires PostgreSQL 15");
                connection.setAutoCommit(false);
                String scratch="contact_baseline_"+UUID.randomUUID().toString().replace("-","");
                List<String> actual=fingerprint(connection,"public");
                try(var statement=connection.createStatement()) {statement.execute("CREATE SCHEMA "+scratch);}
                connection.setSchema(scratch);
                ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V1__legacy_contact_schema.sql"));
                List<String> expected=fingerprint(connection,scratch);
                connection.rollback(); connection.setSchema("public"); connection.setAutoCommit(true);
                if(!actual.equals(expected)) throw new IllegalStateException("Legacy schema drift detected; baseline refused");
                // The maintainer must stop the old application during this maintenance operation.
                Flyway.configure().dataSource(source).defaultSchema("public").baselineVersion("1")
                    .baselineDescription("Verified legacy contact schema").baselineOnMigrate(false).load().baseline();
            } finally {
                if(!connection.getAutoCommit()) connection.rollback();
                connection.setAutoCommit(true);
                try(var lock=connection.createStatement()) {lock.execute("SELECT pg_advisory_unlock(440042)");}
            }
        }
    }
    private static String version(Connection c) throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery("SHOW server_version")) {r.next();return r.getString(1);}
    }
    private static List<String> fingerprint(Connection c,String schema) throws SQLException {
        List<String> parts=new ArrayList<>();
        String[] queries={
            "SELECT 'T|'||table_name||'|'||table_type FROM information_schema.tables WHERE table_schema=?",
            "SELECT 'C|'||table_name||'|'||column_name||'|'||udt_name||'|'||is_nullable||'|'||COALESCE(character_maximum_length::text,'')||'|'||COALESCE(datetime_precision::text,'')||'|'||COALESCE(column_default,'')||'|'||COALESCE(collation_name,'')||'|'||is_identity||'|'||is_generated||'|'||COALESCE(generation_expression,'') FROM information_schema.columns WHERE table_schema=?",
            "SELECT 'K|'||cl.relname||'|'||co.conname||'|'||pg_get_constraintdef(co.oid) FROM pg_constraint co JOIN pg_class cl ON cl.oid=co.conrelid JOIN pg_namespace ns ON ns.oid=cl.relnamespace WHERE ns.nspname=?",
            "SELECT 'I|'||tablename||'|'||indexname||'|'||indexdef FROM pg_indexes WHERE schemaname=?",
            "SELECT 'R|'||routine_name FROM information_schema.routines WHERE routine_schema=?",
            "SELECT 'S|'||sequence_name FROM information_schema.sequences WHERE sequence_schema=?",
            "SELECT 'G|'||event_object_table||'|'||trigger_name FROM information_schema.triggers WHERE trigger_schema=?",
            "SELECT 'P|'||cl.relname||'|'||cl.relrowsecurity::text||'|'||cl.relforcerowsecurity::text FROM pg_class cl JOIN pg_namespace ns ON ns.oid=cl.relnamespace WHERE ns.nspname=? AND cl.relkind IN ('r','p')",
            "SELECT 'Q|'||tablename||'|'||policyname FROM pg_policies WHERE schemaname=?"
        };
        for(String query:queries) try(var s=c.prepareStatement(query)) {
            s.setString(1,schema);
            try(var r=s.executeQuery()) {while(r.next()) {
                String value=r.getString(1).replace(schema+".","");
                parts.add(value.startsWith("K|")?canonicalConstraint(value):value);
            }}
        }
        Collections.sort(parts);return parts;
    }

    // pg_dump/restore can move varchar->text casts from an enum array to its
    // elements. Ignore only those compatible casts/formatting, outside quotes.
    // Preserve every literal, identifier and operator, so widened/changed checks fail.
    private static String canonicalConstraint(String value) {
        int expressionStart=value.indexOf('|',value.indexOf('|',value.indexOf('|')+1)+1)+1;
        var result=new StringBuilder(value.substring(0,expressionStart));char quote=0;
        for(int i=expressionStart;i<value.length();i++) {
            char ch=value.charAt(i);
            if(quote!=0) {
                result.append(ch);
                if(ch==quote) {
                    if(i+1<value.length() && value.charAt(i+1)==quote) result.append(value.charAt(++i));
                    else quote=0;
                }
                continue;
            }
            if(ch=='\'' || ch=='"') {quote=ch;result.append(ch);continue;}
            boolean cast=false;
            for(String allowed:List.of("::character varying","::text[]","::text")) {
                if(value.startsWith(allowed,i)) {i+=allowed.length()-1;cast=true;break;}
            }
            if(!cast && ch!='(' && ch!=')' && !Character.isWhitespace(ch)) result.append(ch);
        }
        return result.toString();
    }
}
