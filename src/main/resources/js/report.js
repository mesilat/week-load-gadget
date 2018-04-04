(function(AJS,$){
    $(function(){
        window.__weekly_activity_gadget__ = {
            report: function(){
                var $dlg = $(Mesilat.TimeSheet.Templates.reportDialog({}));
                $dlg.find('input.aui-date-picker').each(function(){
                    $(this).datePicker({'overrideBrowserDefault': true});
                });
                var dlg = AJS.dialog2($dlg);
                $dlg.find('button.close').on('click', function(e){
                    e.preventDefault();
                    dlg.hide();
                });
                $dlg.find('button.aui-button-primary').on('click', function(e){
                    e.preventDefault();
                    $.ajax({
                        url: AJS.params.baseURL + '/rest/week-load-gadget/1.0/report',
                        type: 'GET',
                        data: {
                            start: $dlg.find('input.com-mesilat-week-load-gadget-report-start').val(),
                            end: $dlg.find('input.com-mesilat-week-load-gadget-report-end').val()
                        },
                        dataType: 'json'
                    }).done(function(data){
                        var $table = $(Mesilat.TimeSheet.Templates.reportTable(data));
                        $dlg.find('div.aui-dialog2-content').empty().append($table);
                    }).fail(function(jqxhr){

                    });
                });
                dlg.show();
            }
        };
    });
})(AJS,AJS.$||$);
