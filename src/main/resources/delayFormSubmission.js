/**
 * Delay form submissions and click events to allow 
 * PrOfESSOS taking a screenshot of the filled formfields.
 */
const delayMillis = 550;
// delay form submit
let forms = document.forms;
for (let frm of forms) {
    if (typeof frm.submit === "function") {

        let origFun = frm.submit;
        frm.submit = function (e) {
            setTimeout(function(){origFun.apply(frm);}, delayMillis);
        };
    }   
}

// also delay button events (may result in delaying twice)
let buttons = document.querySelectorAll('input[type="submit"], button');
for (let btn of buttons) {
    let orig = btn.onclick;
    btn.onclick=function (e){doClick(btn, orig);e.stopPropagation();e.preventDefault();return false;};
    
    let sbm = btn.onsubmit;
    if (sbm) {
        btn.onsubmit = function () {setTimeout(sbm, delayMillis);};
    }
}

function doClick(btn, handler) {
    btn.onclick=handler;
    let fn = function() {btn.click();};
    setTimeout(fn, delayMillis);
    return true;
}
