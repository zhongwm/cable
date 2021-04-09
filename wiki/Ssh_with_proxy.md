Previous: [Composing tasks to run on one host](Composing_tasks_to_run_on_one_host.md)
<div style="text-align: right;">Next: <a href="Ssh_config_file.md">Ssh config file</a></div>

---
# Connect the hosts in another network which you have no route to

Suppose some of your target hosts are in a DMZ zone, you only have a host acting as a bastion
ProxyJumper. In the traditional way of ssh connection, as the way provided by openSSH, you specify
these options to get a jump.

`ssh -o "ProxyJumper=host1"`

or

`ssh -J "host1, user2@host2:port2, host3:port3"`


```
                                       +--------------+              10.0.0.8
          +--------+          10.9.0.3 |              | 10.0.0.4          +-------+
	  | Client |---------------->  |    Bastion   |	  --------------->| Target|
          +--------+                   |              |                   +-------+
                                       +--------------+                          
```

In this case you do

`ssh -J 10.9.0.3 10.0.0.8`

#### More on the options

If you have more than one level of bastion hosts to jump over, which is not unusual in a large
datacenter.

```
+--------+      10.9.0.3 +----------+10.0.0.4   10.0.0.8 +---------+10.2.2.2    10.2.2.52+--------+
| Clinet +-------------->| Bastion1 |------------------->|Bastion2 |-------------------->| Target |
+--------+               +----------+                    +---------+                     +--------+
```

This time you connect to the target host 10.2.2.52, ssh with

`ssh -J "bastion1User@10.9.0.3,bastion2User@10.0.0.8" 10.2.2.52`

openSSH will get you jumped two hops via Bastion1 and Bastion2 to finally get to 10.2.2.52.

Sure you can add port specification on the Jumper term, it's a user@host:port format, so
`bastionUser1@10.9.0.3:2022`.

Also note that you can use a long form of the option, `-o
"ProxyJumper=user1@10.9.0.3,user2@10.0.0.8"` put the jumpers consecutively after the equals sign.

