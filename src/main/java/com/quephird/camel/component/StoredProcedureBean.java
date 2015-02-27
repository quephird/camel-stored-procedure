package com.quephird.camel.component;

import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlInOutParameter;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.SqlReturnResultSet;
import org.springframework.jdbc.object.StoredProcedure;

import javax.sql.DataSource;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This bean, which is intended for use in an Apache Camel route, is
 * configured to invoke a stored procedure.
 */
public class StoredProcedureBean extends StoredProcedure {
    
    private final static String PARAMETER_MODE_IN = "in";
    private final static String PARAMETER_MODE_OUT = "out";
    private final static String PARAMETER_MODE_INOUT = "inout";

    private Set<String> inParameterNames = new HashSet<String>();
    private Map<String, String> inParameterValueFrom = new HashMap<String, String>();	// key = Param Name, val = value source ("body" | Header Name)

    /**
     * Constructor
     *
     * @param dataSource
     * @param sqlTypesClassName    Typically either "java.sql.Types" or "oracle.jdbc.OracleTypes".
     * @param storedProcedureName
     * @param isFunction
     * @param parameters
     * @throws Exception
     */
    public StoredProcedureBean(final DataSource dataSource,
                               final String sqlTypesClassName,
                               final String storedProcedureName,
                               final boolean isFunction,
                               final List<Map<String, Object>> parameters)  throws IllegalArgumentException {
        
        super(dataSource, storedProcedureName);

        Class sqlTypesClass;
        try {
            sqlTypesClass = Class.forName((sqlTypesClassName != null) ? sqlTypesClassName : "java.sql.Types");
        } catch (ClassNotFoundException cnfe) {
            throw new IllegalArgumentException("Cannot find class for sqlTypesClassName: " + sqlTypesClassName);
        }

        setFunction(isFunction);

        // Iterate over all of the stored procedure parameters.
        for (Map<String, Object> parameter : parameters ) {
            String parameterName = (String) parameter.get("name");
            String parameterMode = (String) parameter.get("mode");
            String parameterType = (String) parameter.get("type");
            String parameterValueFrom = (String) parameter.get("valueFrom");   // (optional) "body" means value comes from message body; any other text refers to a header name that the value will come from
            RowMapper parameterRowMapper = (RowMapper) parameter.get("rowMapper");   // (optional) for cursor and ResultSet types, specifies the RowMapper to use; defaults to ColumnMapRowMapper

            if (parameterName == null || parameterMode == null || parameterType == null) {
                throw new IllegalArgumentException("Parameter not sufficiently configured.");
            }

            // Map the declared type of each parameter to the SQL-defined type.
            int sqlType;
            if ("ResultSet".equalsIgnoreCase(parameterType)) {
            	sqlType = 0;  // not used
            } else {
                try {
                    Field f = sqlTypesClass.getField(parameterType.toUpperCase());
                    sqlType = (Integer)f.get(sqlTypesClass);
                } catch (NoSuchFieldException nsfe) {
                    throw new IllegalArgumentException("Invalid parameter type.");
                } catch (IllegalAccessException iae) {
                    throw new IllegalArgumentException("Unable to derive parameter type.");
                }
            }

            // Declare the correct Spring JDBC SqlParameter class according to the parameter mode.
            if (PARAMETER_MODE_IN.equalsIgnoreCase(parameterMode)) {
                declareParameter(new SqlParameter(parameterName, sqlType));
            } else if (PARAMETER_MODE_INOUT.equalsIgnoreCase(parameterMode)) {
                declareParameter(new SqlInOutParameter(parameterName, sqlType));
            } else if (PARAMETER_MODE_OUT.equalsIgnoreCase(parameterMode)) {
                if ("cursor".equalsIgnoreCase(parameterType)) {
                    declareParameter(new SqlOutParameter(parameterName, sqlType, (parameterRowMapper != null) ? parameterRowMapper : new ColumnMapRowMapper()));
                } else if ("ResultSet".equalsIgnoreCase(parameterType)) {
                    declareParameter(new SqlReturnResultSet(parameterName, (parameterRowMapper != null) ? parameterRowMapper : new ColumnMapRowMapper()));
                } else {
                    declareParameter(new SqlOutParameter(parameterName, sqlType));
                }
            } else {
                throw new IllegalArgumentException("Invalid parameter mode.");
            }

            // Handle the IN(OUT) parameters. 
            if (PARAMETER_MODE_IN.equalsIgnoreCase(parameterMode) || PARAMETER_MODE_INOUT.equalsIgnoreCase(parameterMode)) {
                inParameterNames.add(parameterName);
                if (parameterValueFrom != null) {
                    inParameterValueFrom.put(parameterName, parameterValueFrom);
                }
            }
        }

        // Prepare the procedure call using the Spring JDBC StoredProcedure method.
        compile();
    }

    /**
     * This method is used to include only IN and INOUT parameters
     * when the procedure is actually invoked and its results processed.
     *
     * @return Set<String> The set of IN and INOUT parameters
     */
    public Set<String> getInParameterNames() {
        return this.inParameterNames;
    }

    /**
     * This method is used when mapping "alternate" values to IN(OUT) parameters.
     * Normally the value for a given parameter will come from the message header of the same name.
     * This functionality allows the value to come from either the message body or a message header of a different name.
     *
     * @return Map<String, String> key = Param Name, val = value source ("body" | Header Name)
     */
    public Map<String, String>  getInParameterValueFrom() {
        return this.inParameterValueFrom;
    }

    /**
     * This is the method that is called from the Camel flow to invoke
     * the stored procedure, and return its results.
     *
     * @param body     The inbound Camel message body
     * @param exchange The Camel Exchange object
     *
     * @return Map<String, Object> A Map of out parameter names and their correspondent values.
     */
    @Handler
    public Map<String, Object> process(final Object body, final Exchange exchange) {
        
        // Get all of the inbound headers so that we can obtain the parameter values.
        Map<String, Object> inHeaders = exchange.getIn().getHeaders();

        // But there are other name/value pairs included in the payload that need to be filtered out.
        Map<String, Object> inParameters = new HashMap<String, Object>(inHeaders); 
        inParameters.keySet().retainAll(this.getInParameterNames());

        // Map alternate values.
        for (Map.Entry<String, String> entry: getInParameterValueFrom().entrySet()) {
            if ("body".equalsIgnoreCase(entry.getValue())) {
                inParameters.put(entry.getKey(), body);
            } else {
                inParameters.put(entry.getKey(), inHeaders.get(entry.getValue()));
            }
        }

        // Finally, execute the procedure and return its results.
           return this.execute(inParameters);
       }
}
