'use strict';

$(function(){
    var lastChange = sessionStorage["lc"] || 0;
    var detectChange = function(){
        $.get( "../docmodified/"+lastChange, function( resp ) {
            var data = resp.data;
            var code = resp.code;
            if(data && code==200 && data.last_change){
                sessionStorage["lc"] = data.last_change;
                location.reload(false);
            }
        }).always(function(){
            setTimeout(detectChange, 10000);
        });
    };

    detectChange();
});
