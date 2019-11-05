package com.mesilat.gadget;

import com.atlassian.jira.ofbiz.DefaultOfBizConnectionFactory;
import org.ofbiz.core.entity.jdbc.dbtype.MsSqlDatabaseType;
import org.ofbiz.core.entity.jdbc.dbtype.Postgres73DatabaseType;
import org.ofbiz.core.entity.jdbc.dbtype.PostgresDatabaseType;
import org.ofbiz.core.entity.jdbc.dbtype.Oracle10GDatabaseType;

public class SQLFactory {
    private static SQLGenerator GENERATOR;
    private static final Object MONITOR = new Object();

    public static String getQueryOne(){
        synchronized(MONITOR){
            if (GENERATOR == null){
                GENERATOR = createInstance();
            }
        }
        return GENERATOR.getQueryOne();
    }
    public static String getQueryTwo(){
        synchronized(MONITOR){
            if (GENERATOR == null){
                GENERATOR = createInstance();
            }
        }
        return GENERATOR.getQueryTwo();
    }
    public static String getQueryTotalWorklog(){
        synchronized(MONITOR){
            if (GENERATOR == null){
                GENERATOR = createInstance();
            }
        }
        return GENERATOR.getQueryTotalWorklog();
    }
    public static String getQueryReport(){
        synchronized(MONITOR){
            if (GENERATOR == null){
                GENERATOR = createInstance();
            }
        }
        return GENERATOR.getQueryReport();
    }
    public static String getQueryUserName(){
        synchronized(MONITOR){
            if (GENERATOR == null){
                GENERATOR = createInstance();
            }
        }
        return GENERATOR.getQueryUserName();
    }

    private static SQLGenerator createInstance() {
        if (DefaultOfBizConnectionFactory.getInstance().getDatasourceInfo().getDatabaseTypeFromJDBCConnection() instanceof MsSqlDatabaseType){
            return new MSSQLGenerator(DefaultOfBizConnectionFactory.getInstance().getDatasourceInfo().getSchemaName());
        } else if (DefaultOfBizConnectionFactory.getInstance().getDatasourceInfo().getDatabaseTypeFromJDBCConnection() instanceof PostgresDatabaseType
                || DefaultOfBizConnectionFactory.getInstance().getDatasourceInfo().getDatabaseTypeFromJDBCConnection() instanceof Postgres73DatabaseType){
            return new PostgreSQLGenerator();
        } else if (DefaultOfBizConnectionFactory.getInstance().getDatasourceInfo().getDatabaseTypeFromJDBCConnection() instanceof Oracle10GDatabaseType) {
            return new OracleSQLGenerator();
        } else {
            return new DefaultGenerator(); // MySQL, H2
        }
    }
}