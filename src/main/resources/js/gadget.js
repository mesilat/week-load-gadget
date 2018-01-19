define('com.mesilat/week-load-gadget', ['jquery'], function($){
    function time2text(time){
        if (time === 0)
            return '';

        var hours = Math.floor(time);
/*
        } else if (time - hours < 0.25){
            return '' + (hours > 0? hours: '');
        } else if (time - hours < 0.5){
            return '' + (hours > 0? hours: '') + '&frac14;';
        } else if (time - hours < 0.75){
            return '' + (hours > 0? hours: '') + '&frac12;';
        } else {
            return '' + (hours > 0? hours: '') + '&frac34;';
        }
*/
        var minutes = Math.floor((time - hours)*60);
        return minutes < 10? '' + hours + ':0' + minutes: '' + hours + ':' + minutes;
    }
    function create(gadget,data){
        var issues = {};
        data.issues.forEach(function(rec){
            if (!(rec.id in issues)){
                issues[rec.id] = {
                    id:       rec.id,
                    issuekey: rec.issuekey,
                    summary:  rec.summary,
                    resolved: rec.resolved,
                    days: {}
                };
            }
            if (!(rec.day in issues[rec.id].days)){
                issues[rec.id].days[rec.day] = {
                    time: 0
                };
            }
        });
        data.worklog.forEach(function(rec){
            issues[rec.id].days[rec.day].time += rec.time / 3600;
            if (rec.comment !== ''){
                if ('comments' in issues[rec.id].days[rec.day]){
                    issues[rec.id].days[rec.day].comments += '\n' + rec.comment;
                } else {
                    issues[rec.id].days[rec.day].comments = rec.comment;
                }
            }
        });

        var total = {};

        for (var key in issues){
            issues[key].total = 0;
            for (var day = 1; day <= 7; day++){
                if (day in issues[key].days){
                    var time = issues[key].days[day].time;
                    if (!(day in total)){
                        total[day] = 0;
                    }
                    total[day] += time;
                    issues[key].total += time;
                    issues[key].days[day].time = time2text(issues[key].days[day].time);
                }
            }
            if (issues[key].total > 0){
                issues[key].total = time2text(issues[key].total);
            } else {
                issues[key].total = time2text(issues[key].total);
            }
        }
        var grandTotal = 0;
        for (var day in total){
            grandTotal += total[day];
            total[day] = time2text(total[day]);
        }
        total[0] = time2text(grandTotal);

        var params = {
            week:    data.week,
            weeks:   data.weeks,
            days:    data.days,
            period:  data.period,
            user:    data.user,
            display: data.display,
            issues:  issues,
            total:   total,
            baseUrl: data.baseUrl
        };
        //console.log('com.mesilat.week-load-gadget', params);
        var $html = $(Mesilat.TimeSheet.Templates.week(params));
        $html.find('td.week-load-gadget-day').each(function(){
            var $td = $(this);
            var rec = issues[$td.attr('data-rec')];
            var day = $td.attr('data-day');
            $td.find('div').each(function(){
                var $div = $(this);
                $div.width(50);
                if (day in rec.days){
                    $div.on('click', function(e){
                        e.preventDefault();
                        window.open(AJS.contextPath() + '/secure/CreateWorklog!default.jspa?id=' + rec.id, '_blank').focus();
                    });
                    if (rec.days[day].time === ''){
                        $div.addClass('week-load-gadget-activity');
                    } else {
                        $div.addClass('week-load-gadget-work');
                        $div.html(rec.days[day].time);
                    }
                    if ('comments' in rec.days[day]){
                        $div.addClass('week-load-gadget-comments');
                        $div.attr('title', rec.days[day].comments);
                    }
                }
            });
        });
        
        $html.find('table.week-load-gadget-navigator a').each(function(){
            $(this).on('click', function(e){
                var $a = $(e.target);
                e.preventDefault();
                $.ajax({
                    url: '/rest/week-load-gadget/1.0/week',
                    type: 'GET',
                    data: {week: $a.closest('td').attr('data:week')},
                    dataType: 'json',
                    success: function(data){
                        gadget.getView().html(create(gadget,data));
                        gadget.resize();
                    },
                    failure: function(jqxhr){
                        console.log('com.mesilat.weel-load-gadget', jqxhr.responseText);
                    }
                });
            });
        });

        return $html;
    }

    return {
        create: create
    };
});