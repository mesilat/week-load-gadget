define('com/mesilat/weekload/gadget', [ 'require' ], function(require) {
    var $ = require('jquery');

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
    };
    function getSettings(){
        if (window.localStorage){
            if ('weekloadSettings' in window.localStorage){
                return JSON.parse(window.localStorage.weekloadSettings);
            }
        } else if (window.__weekloadSettings) {
            return window.__weekloadSettings;
        }

        return {
            showWeekend: true,
            narrow: false
        };
    }
    function saveSettings(settings){
        if (window.localStorage){
            window.localStorage.weekloadSettings = JSON.stringify(settings);
        } else {
            window.__weekloadSettings = settings;
        }
    }

    function create(gadget, data, _window){
        var settings = getSettings(),
            issues = {};

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

        var weeks = [];
        _.keys(data.weeks).forEach(function(key){
            weeks.push({
                key: key,
                text: data.weeks[key]
            });
        });
        weeks.sort(function(a,b){
            return b.key.localeCompare(a.key);
        });
        
        var params = {
            week:       data.week,
            weeks:      weeks,
            days:       data.days,
            //period:     data.period,
            user:       data.user,
            //display:    data.display,
            issues:     issues,
            total:      total,
            baseUrl:    data.baseUrl,
            isLicensed: data.isLicensed,
            showWeekend:!!settings.showWeekend,
            narrow:     !!settings.narrow
        };

        //console.log('com/mesilat/weekload/gadget create', settings, params);

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

        $html.find('div.week-load-gadget-tabs a:not(.com-mesilat-week-load-gadget-menu)').each(function(){
            $(this).on('click', function(e){
                e.preventDefault();
                var week = $(this).closest('span').attr('data:week'),
                    tabs = 0;
                $(this).closest('div.week-load-gadget-tabs').find('span').each(function(){
                    var $span = $(this);
                    if (this.hasAttribute('data:week') && $span.position().top === 0){
                        tabs++;
                    }
                });
                $.ajax({
                    url: '/rest/week-load-gadget/1.0/week',
                    type: 'GET',
                    data: {
                        week: week,
                        tabs: tabs
                    },
                    dataType: 'json',
                    success: function(data){
                        gadget.getView().html(create(gadget, data, _window));
                        gadget.resize();
                    },
                    failure: function(jqxhr){
                        console.log('com/mesilat/weekload/gadget', jqxhr.responseText);
                    }
                });                    
            });
        });

        $html.find('#com-mesilat-weekload-show-report').on('click', function(e){
            e.preventDefault();
            _window.__weekly_activity_gadget__.report();
        });

        $html.find('#com-mesilat-weekload-show-weekend').on('click', function(e){
            setTimeout(function(){
                settings.showWeekend = $(e.target).attr('aria-checked') === 'true';
                saveSettings(settings);
                $(e.target).closest('aui-dropdown-menu').remove();
                gadget.getView().html(create(gadget, data, _window));
                gadget.resize();
            });
        });

        $html.find('#com-mesilat-weekload-show-narrow').on('click', function(e){
            setTimeout(function(){
                settings.narrow = $(e.target).attr('aria-checked') === 'true';
                saveSettings(settings);
                $(e.target).closest('aui-dropdown-menu').remove();
                gadget.getView().html(create(gadget, data, _window));
                gadget.resize();
            });
        });

        return $html;
    }

    return create;
});

AJS.namespace("weekLoadGadget", null, require("com/mesilat/weekload/gadget"));