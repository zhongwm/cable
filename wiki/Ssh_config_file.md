Previous: [Ssh to host out of your network](Ssh_with_proxy.md)

### Ssh client side config file

There is a config file for your client side ssh usage, on most Unix systems, there are two levels of
config, for user level, it's `$HOME/.ssh/config`, for the system level, its' `/etc/ssh/ssh_config`

When you look at one of the ssh client side config file, it lists a bunch of hosts, some host
patterns. In each item, there are some options, some of which are related to the proxy jumpers,
including `ProxyJumper`, `ProxyCommand`.

These two options specifies proxy setting for a host or host pattern, in two different approaches.
One is to specify a given command to serve as the hopper, thus you have more control on how the
jumper is made. It's usually a normal ssh command with an `-W %h:%p` option.

