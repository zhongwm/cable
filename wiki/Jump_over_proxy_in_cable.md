Previous: [Ssh config file](Ssh_config_file.md)

---
# Jump over proxies in cable

Cable supports all the proxy config from your ssh config file, your $HOME/.ssh/config, your
/etc/ssh/ssh_config, your ProxyCommand options in long and short format "-J".

Having your proxies set in the config files, with cable, you name the host to connect to. the proxy
jumping things are done for you behind the scene. Whether you use Static constructing, dynamic
constructing, or even the cable-zssh primitives.

### Examples

Assume that you have your ssh config in ~/.ssh/config like this:

```
Host FeatureMachine*
    ProxyCommand sshpass -p 'proxyaw354w^&%pas_-od' ssh -q -vNJms -i ~/.ssh/id_rsa -l 18612341234 -W %h:%p -p 2022 192.168.100.12

Host RegressionJumper
    Hostname 192.168.83.254
    User JohnDoe
    Port 2065
    
Host RegressionMachine1
    Host 192.168.83.23
    ProxyJumper Jane@RegressionJumper

Host 10.0.1.*
    ProxyJumper William@172.16.8.21:1022, host2 

```


##### By name

Now constructing the tasks

```scala
import zhongwm.cable.zssh.TypeDef._
import zhongwm.cable.zssh.TypeDef.HostConnS._
import zhongwm.cable.zssh.Zssh._
...
Parental(
    JustConnect("RegressionMachine1", password = Some("test")),
    Action("testHost2", password = Some("test"), action = scriptIO("hostname"))
  )
```

When you run that, you get 2 hops to get to `testHost2`.

##### By address

```scala
Parental(
    JustConnect("10.0.1.231", password = Some("test")),
    Action("testHost2", password = Some("test"), action = scriptIO("hostname"))
  )
```

Again, if you give the hostName to connect by address, which happens to be a preconfigured target
host which needs to jump over some proxy, in our case, the `Host 10.0.1.*` section. You also get the
connection jumped as what is configured.

When we give it a password in the task, the password will be used to connect. We can omit it and use
a private key, it can be your default .ssh rsa ssh key. Cable will read your ssh key from that file
and use that key for authentication.

Since we already supports multi level proxies jumping in cable, for the sub-level ssh tasks, we
deliberately don't respect the proxies specified in ssh config, even if it is configured. Because we
know we that we had given it a parent which acts just like it's proxy.
