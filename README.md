# Elements

## Introduction

Elements is a framework that is used to build applications.  With Elements, an application consists of many components (which are called Atoms).  Essentially, to create an application, one only has to declare a set of Atoms and a "provision" file that references them.  Within an Atom, the inter-dependencies between objects are wired using Google Guice and their properties are configured using YAML.  In a way, it is similar to the Spring Framework.  However, it does away with XML and allows developers to use Groovy whenever necessary.  The end result is that the configuration files are much more terse and flexible.  

When compared to Spring Boot, Elements stays away from configuration in code, i.e. via annotations.  Rather, the prefer method is to use a provision file and Atoms.  The fundamental philosophy is that given a set of Atoms, one can deploy different applications using different provision files.  The bottom line is that it aims to be simpler and less verbose than Spring and it allows configuration using groovy scripts so that with a deployment bundle, one can employ different provision files to start different applications.  Think of it as analogous to Java's WORA (write once, run anywhere): BORA (build once, run applications)

Finally, Elements comes with a set of easy to use Atoms that allow developers to quickly set up database thread pools, JPA, Hibernate, RESTful services etc.  In fact, one of the most sophisticated payment system, Tritium by Episode Six, was built using Elements.

## Resources and ResourceManager
When a provision file is loaded, it is associated with an instance of ResourceManager.  The ResourceManager serves three purposes.  First, it is for binding classes or singletons so that they are always available during dependency injection.  Second, it is for creating Resources instances.  A ResourceManger can be configured with a number of ResourceProviders via Atoms.  During a call to open a Resources instance, it coordinates with all of the ResourceProviders so that resources, such as a database transaction, are open.  When the Resources instance is committed or aborted, the ResourceProviders are responsible for committing or aborting the resources they manage.  Third, it is a centralized registry for POJOs.  Objects can be registered with a ResourceManager by name for other objects to look up.  

An example of using Resources would be declaring it as an injection point for a RESTful service.  When a method is called, the Resources instance would be open.  Once the call is completed, it would either be committed or aborted depending on whether an exception was thrown.  Below is a simple example showing using a Resources instance to retrieve a JPA EntityManager.  In this case, it is assumed a ResourceProvider for JPA EntityManager has already been registered with the ResourceManager.

    public class Restful {
        @Inject
        protected Resources resources;
        
        @POST
        @Produces({MediaType.APPLICATION_JSON})
        @Path("person")
        public Person createPerson(Person person) {
            EntityManager em = resources.getInstance(EntityManager.class);
            em.persist(person);
            return Person;
        }
    }

### Resources Open/Commit/Abort
For calls on non-singleton RESTful services, Resources management is done automatically.  However, there are many cases that the code needs to manage Resources, for instance, singleton RESTful services and cron jobs.  First the object needs to be injected with a Provision:

    @Inject
    Provision provision;
    
The code then can use the Provision instance to commit a closure.  The decision to use closures hinges on the ability to control open and commit/abort resources cleanly.  More importantly, it also allows the closure to be retried if an exception is thrown.

The Provision class has several commit methods that take various instance lookup parameters.  In the simplest form, it takes no lookup parameters.

	// no parameters
    provision.commit(()-> {
    })
    
The one parameter version is a convenient method and is equivalent to opening a Resources instance and looks up a bound instance and provides it to the closure.  Ditto for methods with higher number of parameters.

    // one parameter example
    provision.commit(Resources.class, (resources)-> {
        Person person = resources.newInstance(Person.class);
    })
    
    // two parameters example
    provision.commit(DataSource.class, EntityManager.class, (ds, em)-> {
    })

The general flow of the commit call is the following

* Creates an instance of Resources
* For each ResourceProvider, open its resources
* Executes the closure
* If success, for each ResourceProvider, commits its resources.
* If failed, for each ResourceProvider, aborts its resources.
* If Retry is supported, repeat the process until retry count has reached.

### Resources Retry
TDB

## Provision File
A provision file is a start up script and it is responsible for loading Atoms or other Groovy scripts. The example below shows a simple provision file.

    exec "$__dir/../jobs/**",
        "$__dir/../persist.groovy",
        "$__dir/../restful/**"

It executes all of the groovy scripts in the *$__dir/../jobs* and *$__dir/../restful* directories, and *persist.groovy*.  The variable *__dir* is automatically set to the directory that contains the script currently being executed.  It uses Ant's directory syntax: ** means files in all subdirectories where as * means just files in the directory.

### Launching a Provision
To launch a provision file, the general syntax is
    
    java -cp <classpath> net.e6tech.elements.common.launch.Launch 
        launch=<path to the provision file> end

As one may surmise, you can launch more than one provision file.

### Atom
An Atom file is a Groovy script that contains Atoms.  Here is an very simple Atom for providing two RESTful services: HelloWorld and Goodbye.

    import ...JaxRSServer
    import ...Utility
    
    atom("hellworld") {
        configuration =  """
        _helloWorld.addresses:
            - "http://0.0.0.0:9001/restful/"
        _helloWorld.resources:
            - class: "net.e6tech.sample.web.cxf.HelloWorld"
              singleton: false
            - class: "net.e6tech.sampl.web.cxf.Goodbye"
              singleton: true
        _utility: 
            name: just an example
            count: 3
     """
     
        _helloWorld = JaxRSServer
        _utility = Utility
    }
    

The Java implementation of HelloWorld, 

    @Path("/helloworld")
    public class HelloWorld {
    
        @Inject
        ResourceManager resourceManager;
        
        @Inject
        Utility util;
    
        @Inject
        Resources resources;
    
        @GET
        @Produces({MediaType.APPLICATION_JSON})
        @Path("get/{id}")
        public String getHello(@PathParam("id") int id) {
            EntityManager em = resources.getInstance(EntityManager.class);
            // do something with em, for example lookup an entity using the id.
            String ret =  ...
            return ret;
        }
    }

Atom must have a name and it must be unique so that it won't be loaded twice.  Within an Atom, the first section is configuration and it is a multiline YAML string that is used to configure POJOs.  The POJOs are declared after the configuration section.  The syntax is 'name = <Java Class>'.  The naming convention is very important in that for a POJO of which name DOES NOT start with a underscore, it is automatically registered with the ResourceManager.  In general, A POJO's name should start with a underscore unless there is a particular reason to register it with the ResourceManager so that other objects can look it up by name.  However, please note that it is better to use injection instead of named lookup.

As mentioned, the YAML string is used to configured POJOs.  In this example, _helloworld is declared to be an instance of JaxRSServer so that at this point of the script, an instance of JaxRSServer is acutally instantiated.  It has two fields, addresses (List<String>) and resources (List<Map<String, Object>>), and they are set up by the "_helloword" portion of the YAML string.

In the example, it also demonstrates injection.  HelloWord requires a Utility instance to be injected.  By declaring a Utility instance in the Atom, it is automatically injected into _helloworld.

    
## Resource Providers

### Hibernate Resource Provider
Elements provides Hikari database connection pool and support for JPA via Hibernate.  It is rather straightforward to create a Hikari DataSource as shown below.  One can, however, use other types of DataSource.

    atom("datasource") {
    configuration = """
        dataSource:
            driverClassName: org.mariadb.jdbc.Driver
            username: test
            password: password
            jdbcUrl: "jdbc:mariadb://127.0.0.1:3306/h3"
    """
        dataSource = HikariDataSource
    }
    
Once a DataSource is created, the script blow shows how to configure a Hibernate JPA provider.  Elements comes with a HibernateEntityManagerProvider to assist the process.  Since HiberanteEntityManagerProvider is a subclass of ResourceProvider, it is automatically registered with the ResourceManager.
    
    atom("persist") {
    configuration = """
        entityManagerProvider.persistenceUnitName: h3
        entityManagerProvider.transactionTimeout: ${entityManagerTxTimeout}
        entityManagerProvider.monitorTransaction: ${entityManagerMonitorTransaction}
        entityManagerProvider.longTransaction: ${entityManagerLongTransaction}
        entityManagerProvider.persistenceProperties:
            javax.persistence.nonJtaDataSource: ^dataSource
    """
    
        entityManagerProvider = HibernateEntityManagerProvider

        postInit {
            // testing if EntityManager can be created correctly
            open({ resources ->
                EntityManager em = resources.getInstance(EntityManager)
                resources.abort()
            })
        }
    }

### Persistence XML


    <persistence xmlns="http://xmlns.jcp.org/xml/ns/persistence"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/persistence classpath://xml/ns/persistence/persistence_2_1.xsd"
             version="2.1">
      <persistence-unit name="h3">
        <provider>org.hibernate.jpa.HibernatePersistenceProvider</provider>
        <!-- <non-jta-data-source>java:dataSource/h3</non-jta-data-source> -->
        <exclude-unlisted-classes>true</exclude-unlisted-classes>
        <shared-cache-mode>ENABLE_SELECTIVE</shared-cache-mode>
        <properties>
            <!-- <property name="hibernate.connection.provider_class" value="provider class" /> -->
            <property name="hibernate.ejb.cfgfile" value="persistence/h3/h3.cfg.xml" />
            <property name="current_session_context" value="thread" />
            <property name="hibernate.cache.use_query_cache" value="false" />
            <property name="hibernate.cache.use_second_level_cache" value="false" />
        </properties>
      </persistence-unit>
    </persistence>

### Hibernate Configuration
The important part in the h3.cfg.xml is the configuration of session scoped interceptor.

    <?xml version='1.0' encoding='utf-8'?>

    <>hibernate-configuration>
      <session-factory name="h3">
          <!-- properties -->
          <property name="dialect">org.hibernate.dialect.MySQL57InnoDBDialect</property>
          <property name="show_sql">${hibernate.show_sql}</property>
          <property name="hibernate.connection.isolation">2</property> <!-- read uncommitted is 1, read committed is 2 etc. see java.sql.Connection -->
          <property name="hibernate.cache.use_query_cache">true</property>
          <property name="hibernate.cache.region.factory_class">org.hibernate.cache.ehcache.SingletonEhCacheRegionFactory</property>
          <property name="hibernate.cache.use_second_level_cache">${hibernate.cache.use_second_level_cache}</property>
          <!--<property name="hibernate.cache.use_structured_entries">true</property>-->
          <property name="hibernate.generate_statistics">${hibernate.generate_statistics}</property>
          <property name="hibernate.archive.autodetection">false</property>
          <property name="hibernate.ejb.interceptor.session_scoped">net.e6tech.elements.persist.hibernate.Interceptor</property>
          <!-- <property name="hibernate.ejb.interceptor">net.e6tech.elements.persist.hibernate.Interceptor</property> -->

          <!-- mapping files -->
          <mapping resource="persistence/h3/mappings/account.hbm.xml"/>
        ...
      </session-factory>
    </hibernate-configuration>

## RESTful Services

## Batch Jobs