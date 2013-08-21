package com.quephird.camel.component;

import org.apache.camel.Exchange;
import org.apache.camel.Handler;

import org.springframework.jdbc.core.SqlInOutParameter;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;

import org.springframework.jdbc.object.StoredProcedure;

import javax.sql.DataSource;

import java.lang.reflect.Field;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This bean is
 */
public class StoredProcedureBean extends StoredProcedure {
    private final static String PARAMETER_MODE_IN = "in";
    private final static String PARAMETER_MODE_OUT = "out";
    private final static String PARAMETER_MODE_INOUT = "inout";

    private Set<String> inParameterNames = new HashSet<String>();

    /**
     *
     *
     * @param dataSource
     * @param storedProcedureName
     * @param isFunction
     * @param parameters
     * @throws Exception
     */
    public StoredProcedureBean(final DataSource dataSource,
                               final String storedProcedureName,
                               final boolean isFunction,
                               final List<Map<String, String>> parameters) throws Exception {
        setDataSource(dataSource);
   	    setSql(storedProcedureName);
        setFunction(isFunction);

        // Iterate over all of the stored procedure parameters...
        for (Map<String, String> parameter : parameters ) {
            String parameterName = parameter.get("name");
            String parameterMode = parameter.get("mode");
            String parameterType = parameter.get("type");

            // ... Map the declared type of each parameter to the SQL type defined in java.sql.Types...
            Class sqlTypesClass = java.sql.Types.class;
            Field f = sqlTypesClass.getField(parameterType.toUpperCase());
            int sqlType = (Integer)f.get(sqlTypesClass);

            // ... and select the correct Spring JDBC SqlParameter class according to the inbound mode.
            if (PARAMETER_MODE_IN.equalsIgnoreCase(parameterMode)) {
                declareParameter(new SqlParameter(parameterName, sqlType));
                // Remember to add this parameter to the internal list.
                inParameterNames.add(parameterName);
            } else if (PARAMETER_MODE_OUT.equalsIgnoreCase(parameterMode)) {
                declareParameter(new SqlOutParameter(parameterName, sqlType));
            } else if (PARAMETER_MODE_INOUT.equalsIgnoreCase(parameterMode)) {
                declareParameter(new SqlInOutParameter(parameterName, sqlType));
            } else {
                throw new Exception("Invalid parameter mode.");
            }
        }

        // Prepare the procedure call using the Spring JDBC StoredProcedure method.
        compile();
    }

    /**
     * This method is used to include only IN parameters
     * when the procedure is actually invoked and its results processed.
     *
     * @return Set<String> The set of IN only parameters
     */
    public Set<String> getInParameterNames() {
        return this.inParameterNames;
    }

    /**
     * This is the method that is called from the Camel flow to invoke
     * and execute the stored procedure, and return its results.
     *
     * @param body     The inbound Camel message body
     * @param exchange The Camel Exchange object
     *
     * @return Map<String, Object> A Map of out parameter names and their correspondent values.
     */
    @Handler
    public Map<String, Object> process(final String body, final Exchange exchange) {
        // Get all of the inbound headers so that we can obtain the parameter values.
        Map<String, Object> inParameters = exchange.getIn().getHeaders();

        // Unfortunately, there are other name/value pairs included in the payload...
        Set<String> inParameterNames = this.getInParameterNames();

        // ... so we need to filter those out...
        inParameters.keySet().retainAll(inParameterNames);

        // ... and then we can finally execute the procedure and return its results.
   		return this.execute(inParameters);
   	}
}
