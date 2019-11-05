package com.mesilat.gadget;

import java.text.MessageFormat;

public class MSSQLGenerator extends DefaultGenerator {
    public MSSQLGenerator(String schemaName){
        super("mssql");
        
        sql1 = MessageFormat.format(sql1, schemaName);
        sql2 = MessageFormat.format(sql2, schemaName);
        sql3 = MessageFormat.format(sql3, schemaName);
        sql4 = MessageFormat.format(sql4, schemaName);
        sql5 = MessageFormat.format(sql5, schemaName);
    }
}