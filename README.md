## Description

This is a trivial project which demonstrates how a Spring bean can be configured for a particular stored procedure and participate in an Apache Camel route.

## Set up and installation

Steps to setting up your environment and running this demonstration:

1. Find an available Oracle database environment, OR
   install Oracle XE 11g. Go to http://www.oracle.com/technetwork/database/express-edition/overview/index.html to download it and its documentation.
2. Run the SQL scripts in the src/main/sql directory to create the example procedures and functions.
3. Set the database URL, username, and password in the spring-beans.xml file in src/main/resources.
4. Run 'mvn clean install'.

## Background

Camel comes with two components, namely the SqlComponent and JdbcComponent, that allow for execution of SQL queries and DML statements.
The difference between the two is in usage: the former allows for greater flexibility whereas the latter only allows for the SQL statement in question to be passed in the message body.
For more details see http://camel.apache.org/sql-component.html and http://camel.apache.org/jdbc.html.
Neither, however, allows for invocation of stored procedures as doing so does not map to a single SELECT, INSERT, UPDATE, or DELETE SQL statement.
Of course, simple stored functions can be wrapped in a SELECT...

select  some_function()
from    dual
/
... but this strategy does not work in general as
1. stored procedures do not return values and can instead have OUT parameters, and
2. stored functions do not necessarily return scalar values, as required if used in SQL queries.

Additionally, there is no way to configure either component to invoke a stored procedure or somehow subclass either one and override some of its behaviors to accomplish this goal.
In short, both components utilize the JDBC PreparedStatement class which is only good for single SQL DML statements enumerated above.
Only CallableStatements can be used to call stored procedures and so either implementation would have to be essentially rewritten.

The optimal solution is to be able to use a component that allows a developer to specify all of the elements of this problem, namely:

1. The name of the stored procedure
2. Whether or not the stored procedure is a function
3. The set of parameters including their
  * Names
  * Modes (IN, OUT, or INOUT)
  * Types

Luckily, Spring provides a relatively simple means calling stored procedures using the org.springframework.jdbc.object.StoredProcedure class.
Moreover, Camel allows for beans to participate in routes and send messages to them.

At a minimum, there are two requirements for this bean:

* exposing of a constructor which allows for the passage of all needed configuration parameters
* a method which can get a reference to the inbound org.apache.camel.Exchange object to retrieve the actual values of the parameters to be passed to the stored procedure

## Running a Camel route

The example Spring bean XML configuration file has a bean definition for an Oracle datasource;
you will need to supply the database URL, username, and password:

```xml
<bean id="oracleDataSource" class="org.apache.commons.dbcp.BasicDataSource" destroy-method="close">
    <property name="driverClassName" value="oracle.jdbc.driver.OracleDriver"/>
    <property name="url" value="jdbc:oracle:thin:@some.server.com:1521:SOME_INSTANCE"/>
    <property name="username" value="some_user"/>
    <property name="password" value="some_password"/>
</bean>
```

The simplest example stored procedure is the function which takes no parameters and returns a single scalar value:

```xml
<bean id="functionWithNoParams" class="com.quephird.camel.component.StoredProcedureBean">
    <constructor-arg name="dataSource" ref="oracleDataSource"/>
    <constructor-arg name="storedProcedureName" value="meaning_of_the_universe"/>
    <constructor-arg name="isFunction" value="true"/>
    <constructor-arg name="parameters">
        <list>
            <map>
                <entry key="name" value="foo"/>
                <entry key="mode" value="out"/>
                <entry key="type" value="integer"/>
            </map>
        </list>
    </constructor-arg>
</bean>
```

The route itself is configured below:

```xml
    <camelContext xmlns="http://camel.apache.org/schema/spring">
        <route id="callFunctionWithNoParams">
            <from uri="timer://kickoff?period=1000"/>
            <to uri="bean:functionWithNoParams"/>
            <log message="${body}"/>
        </route>
    </camelContext>
```

To run this route, simple run 'mvn camel:run' at the command line. You should see something like the following:

```
[INFO] --- maven-compiler-plugin:2.3.2:testCompile (default-testCompile) @ camel-stored-procedure ---
[INFO] Nothing to compile - all classes are up to date
[INFO]
[INFO] <<< camel-maven-plugin:2.11.1:run (default-cli) @ camel-stored-procedure <<<
[INFO]
[INFO] --- camel-maven-plugin:2.11.1:run (default-cli) @ camel-stored-procedure ---
[INFO] Using org.apache.camel.spring.Main to initiate a CamelContext
[org.apache.camel.spring.Main.main()] INFO org.apache.camel.main.MainSupport - Apache Camel 2.11.1 starting
Aug 21, 2013 2:37:18 PM org.springframework.context.support.AbstractApplicationContext prepareRefresh
INFO: Refreshing org.springframework.context.support.ClassPathXmlApplicationContext@d37b87: startup date [Wed Aug 21 14:37:18 EDT 2013]; root of context hierarchy
Aug 21, 2013 2:37:18 PM org.springframework.beans.factory.xml.XmlBeanDefinitionReader loadBeanDefinitions
INFO: Loading XML bean definitions from class path resource [spring-beans.xml]
Aug 21, 2013 2:37:19 PM org.springframework.beans.factory.support.DefaultListableBeanFactory preInstantiateSingletons
INFO: Pre-instantiating singletons in org.springframework.beans.factory.support.DefaultListableBeanFactory@52c6f: defining beans [oracleDataSource,functionWithNoParams,functionWithOneParam,procedureWithOneOutParam,procedureWithMultipleOutParams,template,consumerTemplate,camel-1:beanPostProcessor,camel-1]; root of factory hierarchy
[org.apache.camel.spring.Main.main()] INFO org.apache.camel.spring.SpringCamelContext - Apache Camel 2.11.1 (CamelContext: camel-1) is starting
[org.apache.camel.spring.Main.main()] INFO org.apache.camel.management.ManagementStrategyFactory - JMX enabled.
[org.apache.camel.spring.Main.main()] INFO org.apache.camel.impl.converter.DefaultTypeConverter - Loaded 172 type converters
[org.apache.camel.spring.Main.main()] INFO org.apache.camel.spring.SpringCamelContext - Route: callFunctionWithNoParams started and consuming from: Endpoint[timer://kickoff?period=1000]
[org.apache.camel.spring.Main.main()] INFO org.apache.camel.management.DefaultManagementLifecycleStrategy - Load performance statistics enabled.
[org.apache.camel.spring.Main.main()] INFO org.apache.camel.spring.SpringCamelContext - Total 1 routes, of which 1 is started.
[org.apache.camel.spring.Main.main()] INFO org.apache.camel.spring.SpringCamelContext - Apache Camel 2.11.1 (CamelContext: camel-1) started in 0.255 seconds
[Camel (camel-1) thread #0 - timer://kickoff] INFO callFunctionWithNoParams - {foo=42}
[Camel (camel-1) thread #0 - timer://kickoff] INFO callFunctionWithNoParams - {foo=42}
[Camel (camel-1) thread #0 - timer://kickoff] INFO callFunctionWithNoParams - {foo=42}
```

## Useful links

Apache Camel
http://camel.apache.org

Spring JDBC documentation
http://static.springsource.org/spring/docs/3.0.x/spring-framework-reference/html/jdbc.html

## License

Copyright (C) 2013, pɹoɟɟǝʞ uɐp
Distributed under the Eclipse Public License.