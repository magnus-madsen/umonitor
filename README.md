# uMonitor #
uMonitor is a configurable and scriptable monitor of internet services.
It can be used to surveil ftp, imap, pop3, http, and smtp services among others.
uMonitor features an expressive configuration language 
coupled with a simple shell and read-only web interface.

uMonitor is written in the Scala programming language, 
runs on the Java Virtual Machine (JVM) 
and comes packaged as a single executable jar.
The jar is compiled for Java 1.8 and is tested on Linux and Windows.

The source code and binaries are freely available under the MIT license 
which does not restrict commercial nor private use.

## Installation ##
Grab the jar file from the release page and run:

```
  java -jar uMonitor5.jar conf.d
```

where `conf.d` is a directory containing your configuration files.

## Configuration ##
The major components of uMonitor are the `service` and `monitor` declarations.
These make use of the `action` and `contact` declarations. 
Binding all these together are the `bind` and `notify` declarations.

### Services ###
A `service` declaration defines an entity to be surveilled.
For example, here is a simple way to surveil a website URL:

```
service example.com-http-service {
    http {
        url = "http://example.com/";
    }
}
```

This declares a service named `example.com-http-service` which will check every minute whether the URL
`http://example.com` responds with status code `200 OK`. 
A service generates `Up` and `Dn` events, every minute, depending on whether the service responds as expected.

A more sophisticated service declaration may be:

```
service example.com-http-service {
    http {
        url = "http://example.com/";
        status-code = 200;
        good-words = "hello world";
        bad-words = "error", "not found";
    }
}
```

where `status-code` specifies the expected status code, `good-words` and `bad-words` specify which words the response must and must not contain.

We can surveil an ftp service with the declaration:

```
service example.com-ftp-service {
    ftp {
        host = "example.com";
    }
}
```

The `http` and `ftp` declarations are called _connectors_ and can even be combined:

```
service example.com-http-and-ftp-service {
    allOf {
        http {
            url = "http://example.com/";
        }
        ftp {
            host = "ftp.example.com";
        }
    }
}
```

The `allOf` combinator generates an `up` event when _every_ nested connector generates an `up` event.
In contrast, the `oneOf` combinator generates an `up` event when _at least one_ nested connector generates an `up` event.
The `allOf` and `oneOf` connectors can be arbitrarily nested.

## Monitors ##
A `monitor` declaration defines a small state machine used to track the status of one or more services.

For example, we could track the status of the previously declared `example.com-http-service` like this:

```
monitor example.com-http-monitor {
    states {
        Unknown, Online, Offline
    }
    
    when Unknown {
        Up(self) -> Online
        Dn(self) -> Offline
    }
    
    when Online {
        Dn(self) -> Offline
    }
    
    when Offline {
        Up(self) -> Online
    }
}

bind example.com-http-service to example.com-http-monitor as example.com-http-target
```

Although this may seem like a mouthful it is straightforward:
The monitor `example.com-http-monitor` is declared to have three states `Unknown`, `Online` and `Offline`.
The monitor is always in one of these states. 
The initial states (before any events have been received) is the `Unknown` state since it was declared first.
The three `when` clauses are used to declare the transitions between the states. 
Thus, for example, when the monitor is in the state `Online` and sees a down event then it transitions to the `Offline` state.

Here the keyword `self` refers to whichever service the monitor is bound to.
This is what the last line is responsible for: 
It binds the service `example.com-http` to the monitor `example.com-http-monitor` and gives it the name `example.com-http-target`.
That is, a _target name_ is the combination of a service bound to a monitor.

The namespaces for service, monitors and targets are separate, i.e. the same name may be re-used.

Putting all these things together we can write a simple complete configuration:

```
service www.example.com {
    http {
        url = "http://www.example.com";
    }
}

monitor www.example.com {
    states {
        Online, Offline
    }

    when Online {
        Dn(self) -> Offline
    }
    
    when Offline {
        Up(self) -> Online
    }
}

bind www.example.com to as www.example.com as www.example.com
```

Note that the `bind` declaration is necessary.

### Clocks and Time Guards ###
Transitions, i.e. changing from one state to another, can be guarded by clock and time guards.

For example, we can define a monitor with a single clock `Downtime`:

```
monitor a-clocked-monitor {
    states {
        Online, Offline
    }
    
    clocks {
        Downtime
    }

    when Online {
        Up(self) -> Online !! Downtime
        Dn(self) if (Downtime > 5min) -> Offline
    }
    
    when Offline {
        Up(self) -> Online !! Downtime
    }
}
```

Like a regular clock, the `Downtime` clock is always running (i.e. increasing its value). 
We can reset a clock, when a transition is taken, by appending `!! <<clock>>` to the transition.
In the above code fragment, the `Downtime` clock is reset whenever an `Up` event is received 
while in either the `Online` or `Offline` state. 
Notice that an explicit self-transition was added from`Online` to `Online` to reset the clock. 

We can also inspect the value of a clock and use it to determine whether a transition can be taken.
Here the condition `if Downtime > 5min` requires that the clock has been running for at least 5 minutes
before the transition can be taken. That is, since services a tested every minute, we must have seen
at least five consecutive failures before the transition to the `Offline` state is made.

The following time units are available: `sec`, `min`, `hour`, `day` and `week`.

A monitor may use multiple clocks, like so:

```
monitor a-two-clocked-monitor {
    states {
        Online, Offline
    }
    
    clocks {
        Uptime, Downtime
    }

    when Online {
        Up(self) -> Online !! Downtime
        Dn(self) if (Downtime > 5min) -> Offline !! Uptime
    }
    
    when Offline {
        Dn(self) -> Offline !! Uptime
        Up(self) if (Uptime > 3min) -> Online !! Downtime
    }
}
```

This monitor transitions to the `Offline` state when it has been down for five minutes, 
and it transitions back to the `Online` state when it has been up for three minutes.

Another type of guard is the _time guard_. 
This can be used to guard a transition based on the time of day or the current week day:

```
monitor a-guarded-monitor {
    states {
        Online, Offline
    }
    
    when Online {
        Dn(self) if (on Monday, Wed, at 7am to 8.30pm) -> Online
        Dn(self) -> Offline
    }
    
    when Offline {
        Up(self) -> Online
    }
}
```

In this monitor, 
if the the weekday is either monday or wednesday and the time is between seven in the morning and eight thirty in the evening, 
then a `Dn` event will transition to the `Online` state. 
At all other times, the second transition takes effect and a `Down` event will transition to the `Offline` state.

The days are `Monday`, `Tuesday`, `Wednesday` and so on (or their abbreviations `Mon`, `Tue`, `Wed`, ...).

A time guard can be specified with either or both of the `on` and `at` components.

## Actions and Contacts ##
In order for uMonitor to be actually useful and notify users of changes in service availability two additional
features are introduced: actions and contacts.

For example, we can declare an action like the following:

```
action notify-offline {
    run {
        exec = "notify-offline.py", "$target-name", "$contact-emails";
    }
}
```

The action is named `notify-offline` and it executes the script specified in the `exec` property.
The script is passed two arguments: 
The name of the service: `$target-name` and 
the list of e-mail addresses associated with the target (more on that later.)

An action is attached to a transition with the `@@` operator, like so:

```
when Online {
    Dn(self) -> Offline @@ notify-offline
}
```

Thus, when a `Dn` event is received in the `Online` state, 
a transition is made to the `Offline` state and the `notify-offline` action is executed.

The following variables may be used in the `exec` property:
 - `$service-name`: the name of the service.
 - `$target-name`: the name of the target.
 - `$event-type`: the type of event (up or down).
 - `$event-timestamp`: the unix timestamp of the event.
 - `$event-message`: the message associated with the event.
 - `$contact-emails`: a comma-separated string of e-mails associated with the target.
 - `$contact-phones`: a comma-separated string of phone numbers associated with the target.
If any of these properties are not available, the literal `"null"` string is passed instead.

Finally, to declare a contact such that its information is provided by `$contact-emails` and `$contact-phones`:

```
contact john-doe {
    email = "john.doe@example.com";
    phone = 12345678;
}
```

and then to associated the contact with a specific target:

```
notify john-doe of www.example.com
```

## Connector Properties ##

#### Cmd Connector ####
| Property        | Semantics                                           | Type        | Opt/Req? |
| --------------- | --------------------------------------------------- | ----------- | -------- |
| path            | path to the executable.                             | String      | Required |
| exit-code       | expected exit code.                                 | Int         | Optional |
| good-words      | words which _must_ appear in the output.            | String List | Optional |
| bad-words       | words which _may not_ appear in the output.         | String List | Optional |
| timeout         | timeout for the execution to complete (in msec).    | Int         | Optional |

#### DNS Connector ####
| Property        | Semantics                                           | Type        | Opt/Req? |
| --------------- | --------------------------------------------------- | ----------- | -------- |
| host            | the hostname or ip address of the server.           | String      | Required |
| domain          | the domain to query.                                | String      | Required |
| address         | the IP the domain is expected to resolve to         | String      | Required |

#### File Connector ####
| Property        | Semantics                                           | Type        | Opt/Req? |
| --------------- | --------------------------------------------------- | ----------- | -------- |
| path            | path to the file.                                   | String      | Required |
| good-words      | words which _must_ appear in the output.            | String List | Optional |
| bad-words       | words which _may not_ appear in the output.         | String List | Optional |

#### FTP Connector ####
| Property        | Semantics                                           | Type        | Opt/Req? |
| --------------- | --------------------------------------------------- | ----------- | -------- |
| host            | the hostname or ip address of the server.           | String      | Required |
| port            | the port of the server.                             | Int         | Optional |
| connect-timeout | the timeout when connecting (in msec).              | Int         | Optional |
| read-timeout    | the timeout when reading (in msec).                 | Int         | Optional |

#### HTTP Connector ####
| Property        | Semantics                                           | Type        | Opt/Req? |
| --------------- | --------------------------------------------------- | ----------- | -------- |
| url             | the url to retrieve.                                | String      | Required |
| statusCode      | the expected http status code.                      | Int         | Optional |
| good-words      | words which _must_ appear in the output.            | String List | Optional |
| bad-words       | words which _may not_ appear in the output.         | String List | Optional |
| connect-timeout | the timeout when connecting (in msec).              | Int         | Optional |
| read-timeout    | the timeout when reading (in msec).                 | Int         | Optional |
| user-agent      | the user-agent sent to the http server.             | String      | Optional |
| keep-alive      | whether to enable keep-alive                        | Boolean     | Optional |
| charset         | default charset, if no charset is provided.         | String      | Required |

#### ICMP Connector ####
| Property        | Semantics                                           | Type        | Opt/Req? |
| --------------- | --------------------------------------------------- | ----------- | -------- |
| host            | the hostname or ip address of the server.           | String      | Required |

#### IMAP Connector ####
| Property        | Semantics                                           | Type        | Opt/Req? |
| --------------- | --------------------------------------------------- | ----------- | -------- |
| host            | the hostname or ip address of the server.           | String      | Required |
| port            | the port of the server.                             | Int         | Optional |
| connect-timeout | the timeout when connecting (in msec).              | Int         | Optional |
| read-timeout    | the timeout when reading (in msec).                 | Int         | Optional |

#### POP3 Connector ####
| Property        | Semantics                                           | Type        | Opt/Req? |
| --------------- | --------------------------------------------------- | ----------- | -------- |
| host            | the hostname or ip address of the server.           | String      | Required |
| port            | the port of the server.                             | Int         | Optional |
| connect-timeout | the timeout when connecting (in msec).              | Int         | Optional |
| read-timeout    | the timeout when reading (in msec).                 | Int         | Optional |

#### RDP Connector ####
| Property        | Semantics                                           | Type        | Opt/Req? |
| --------------- | --------------------------------------------------- | ----------- | -------- |
| host            | the hostname or ip address of the server.           | String      | Required |
| port            | the port of the server.                             | Int         | Optional |
| connect-timeout | the timeout when connecting (in msec).              | Int         | Optional |
| read-timeout    | the timeout when reading (in msec).                 | Int         | Optional |

#### SMTP Connector ####
| Property        | Semantics                                           | Type        | Opt/Req? |
| --------------- | --------------------------------------------------- | ----------- | -------- |
| host            | the hostname or ip address of the server.           | String      | Required |
| port            | the port of the server.                             | Int         | Optional |
| connect-timeout | the timeout when connecting (in msec).              | Int         | Optional |
| read-timeout    | the timeout when reading (in msec).                 | Int         | Optional |

#### SSH Connector ####
| Property        | Semantics                                           | Type        | Opt/Req? |
| --------------- | --------------------------------------------------- | ----------- | -------- |
| host            | the hostname or ip address of the server.           | String      | Required |
| port            | the port of the server.                             | Int         | Optional |
| connect-timeout | the timeout when connecting (in msec).              | Int         | Optional |
| read-timeout    | the timeout when reading (in msec).                 | Int         | Optional |

#### SSH CMD Connector ####
| Property        | Semantics                                           | Type        | Opt/Req? |
| --------------- | --------------------------------------------------- | ----------- | -------- |
| host            | the hostname or ip address of the server.           | String      | Required |
| port            | the port of the server.                             | Int         | Optional |
| username        | the username to use.                                | String      | Required | 
| password        | the password to use.                                | String      | Required |
| command         | the command to execute.                             | String      | Required |
| exitCode        | the expected exit code.                             | Int         | Optional |
| good-words      | words which _must_ appear in the output.            | String List | Optional |
| bad-words       | words which _may not_ appear in the output.         | String List | Optional |

*Note: The SSH CMD connector is likely to change to a different authentication scheme.*
