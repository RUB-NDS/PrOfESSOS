/**
 * Delay form submissions and click events to allow 
 * PrOfESSOS taking a screenshot of the filled formfields.
 * 
 */
const delayMillis = 550;
var forms = document.forms;

for (let frm of forms) {
    let orig = frm.onsubmit;
    if (orig) {
        frm.onsubmit = function () {
            setTimeout(orig, delayMillis);
        };
    } else {
        frm.onsubmit = function (e) {
            setTimeout(function () {
                frm.submit();
            }, delayMillis);
            return false;
        };
    }
    orig = frm.submit;
    frm.submit = function () {setTimeout(function(){orig.apply(frm);}, delayMillis);}
}

var buttons = document.querySelectorAll('input[type="submit"], button[type="submit"]');

for (let btn in buttons) {
    if (btn.click) {
        let orig = btn.click;
        btn.click = function () {setTimeout(function (){orig.apply(btn);}, delayMillis);};
    }
}
