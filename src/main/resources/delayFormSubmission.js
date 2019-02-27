/**
 * Delay form submissions and click events to allow 
 * PrOfESSOS taking a screenshot of the filled formfields.
 * 
 */
const delayMillis = 550;
let forms = document.forms;

for (let frm of forms) {
    let orig = frm.onsubmit;
    if (orig) {
        frm.onsubmit = function () {
            setTimeout(orig, delayMillis);
        };
    } else if (typeof frm.submit === "function") {
        frm.onsubmit = function (e) {
            setTimeout(function () {
                frm.submit();
            }, delayMillis);
            return false;
        };
    }
    orig = frm.submit;
    if (orig) {
        frm.submit = function () {setTimeout(function(){orig.apply(frm);}, delayMillis);}
    }
}

let buttons = document.querySelectorAll('input[type="submit"], button[type="submit"]');

for (let btn of buttons) {

    let orig = btn.onclick;
    btn.onclick=function (e){setTimeout(function(){doClick(btn,orig)},delayMillis); e.stopPropagation();e.preventDefault();return false;};

}

function doClick(btn, handler) {
    handler ? btn.onclick=handler : btn.onclick="";
    btn.click();
}
