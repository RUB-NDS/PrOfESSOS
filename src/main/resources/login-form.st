var username = document.getElementById("j_username");
username.value = "§current_user_username§";

var password = document.getElementById("j_password");
password.value = "§current_user_password§";

if (typeof document.forms[0].submit === "function") {
  document.forms[0].submit();
} else {
    // in the demo OP, the "name" attribute of
    // of the submit button is "submit", clobbering
    // the submit() function
    document.forms[0].submit.click();
}
