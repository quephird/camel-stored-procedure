## Description

This is a trivial project which demonstrates how a Spring bean can be configured for a particular stored procedure and participate in an Apache Camel route.

## Set up and installation

Steps to setting up your environment and running this demonstration:

1a. Find an available Oracle database environment...
                    OR
1b. ... install Oracle XE 11g. Go to http://www.oracle.com/technetwork/database/express-edition/overview/index.html to download it and its documentation.
2. Run the SQL scripts in the src/main/sql directory to create the example procedures and functions.
3. Set the database URL, username, and password in the spring-beans.xml file in src/main/resources.
4. Run 'mvn clean install'.
5. Uncomment the route of interest in spring-beans.xml and run 'mvn camel:run'.

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
 a. Names
 b. Modes (IN, OUT, or INOUT)
 c. Types

Luckily, Spring provides a relatively simple means calling stored procedures using the org.springframework.jdbc.object.StoredProcedure class.
Moreover, Camel allows for beans to participate in routes and send messages to them.

At a minimum, there are two requirements for this bean:

* exposing of a constructor which allows for the passage of all needed configuration parameters
* a method which can get a reference to the inbound org.apache.camel.Exchange object to retrieve the actual values of the parameters to be passed to the stored procedure

## Useful links

Apache Camel:
http://camel.apache.org

Spring JDBC documentation
http://static.springsource.org/spring/docs/3.0.x/spring-framework-reference/html/jdbc.html

## License

Copyright (C) 2013, pɹoɟɟǝʞ uɐp
Distributed under the Eclipse Public License.