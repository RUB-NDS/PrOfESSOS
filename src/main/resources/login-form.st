var username = document.getElementById("j_username");
username.value = "§current_user_username§";

var password = document.getElementById("j_password");
password.value = "§current_user_password§";
// need to call submit.click(), because the
// "name" of the submit button is 'submit'
document.forms[0].submit.click();
