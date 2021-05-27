# Scripting Manual

This document describes how to create and modify tests.


## Basic Structure

Tests are described in the respective xml testplan. The implementation sections can contain `Parameter` elements which
allow to define values which can be used to override the functionality of the implementation.

The `Parameter` element can either contain plain values which are accessible to the implementation, or it can contain a
JavaScript script which is executed in order to produce values such as an id_token claims. The type of parameter is
selected with the `Type` attribute, which can have the value `String` or `JS`. If the attribute is missing, the
parameters type defaults to `String`.

The parameters itself can be used in the implementation programatically. In order to find the correct parameter, each
parameter element must contain a `Key` attribute which is used as a look-up string. In order to keep a list of available
parameters, the classes `OPParameterConstants` and `RPParameterConstants` list all parameters. Note that only the
constants should be used in the implementation in order to maintin the systems integrity.


## Script Context and Functions

In order for a script to be useful, it needs access to the internal state and definitions of the test and it's implementation. For that reason the following variables are available as global objects in the executed script.

- `suiteCtx` Map of String -> Object containing context definitions (see `OPContextConstants`). Context object shared between script invocations in one test step.
- `stepCtx` Map of String -> Object containing test step context definitions (see `OPContextConstants`). Context object shared between script invocations in one test step.
- `impl` Reference to the implementation for which this parameter is defined (e.g. `DefaultOP)`.
- `instParams` Type of `InstanceParameters` containing maps of all string and script parameters-

Additionally it is possible for an implementation to hand over parameters to the executed script with the following global object.

- `params` Map of String -> Object containing values supplied by the implementation.


# Example

A typical use case is to modify values of the user info claims. An evil OP using the honest OPs subject would look as follows.

```
<OPConfig-2>
	<ImplementationClass>de.rub.nds.oidc.server.op.DefaultOP</ImplementationClass>
	<Parameter Key="script_user_info" Type="JS">
		var obj = {
			"sub": impl.getHonestSubject(),
			"name": impl.getTokenName(),
			"preferred_username": impl.getTokenUsername(),
			"email": impl.getTokenEmail()
		};
		obj;
	</Parameter>
</OPConfig-2>
```
