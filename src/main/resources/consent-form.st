var username = document.getElementById("j_username");
username.value = "\§current_user_username\§";

var password = document.getElementById("j_password");
password.value = "\§current_user_password\§";
// add short delay for taking screenshot
setTimeout(function() {document.forms[0].submit();}, 500);
