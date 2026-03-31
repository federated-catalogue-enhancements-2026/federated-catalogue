$(document).ready(function() {

      var $container = $('#participantTable').closest('.container');

      //initialise the datatable
      var partDataTable = $('#participantTable').DataTable({
            ajax: {
                url: 'parts',
                error: function (xhr, error, thrown) {
                    var msg = 'You do not have permission to view participants.';
                    var cls = 'alert-warning';
                    if (xhr.status !== 403) {
                        msg = (xhr.responseJSON && xhr.responseJSON.message) || 'Failed to load participants.';
                        cls = 'alert-danger';
                    }
                    $container.children(':not(.modal)').remove();
                    $container.prepend('<div class="alert ' + cls + ' mt-3">' + msg + '</div>');
                },
                dataSrc: function(json) {
                    return json.items || [];
                }
             },
            scrollX: true,
            order: [[1, 'asc']],
            columns: [
                   {
                            class: 'details-control',
                            orderable: false,
                            data: null,
                            defaultContent: '',
                  },
                  { data: 'id' } ,
                  { data: 'name' },
                  { data: 'publicKey' },
                  { data: 'asset' ,'visible' : false},
                  {
                       orderable: false,
                      "render": function (data) {
                        return '<div><button type="button" class="btn btn-success" id="editButton">Edit</button></div>'
                     }
                  },
                  {
                      orderable: false,
                      "render": function (data) {
                        return '<div><button type="button" class="btn btn-danger" id="deleteButton">Delete</button></div>'
                        }
                  }

                   ]

            } );


            // Array to track the ids of the details displayed rows
                var detailRows = [];

                $('#participantTable tbody').on('click', 'tr td.details-control', function () {
                    var tr = $(this).closest('tr');
                    var row = partDataTable.row(tr);
                    var idx = detailRows.indexOf(tr.attr('id'));

                    if (row.child.isShown()) {
                        tr.removeClass('details');
                        row.child.hide();

                        // Remove from the 'open' array
                        detailRows.splice(idx, 1);
                    } else {
                        tr.addClass('details');
                        row.child(format(row.data())).show();

                        // Add to the 'open' array
                        if (idx === -1) {
                            detailRows.push(tr.attr('id'));
                        }
                    }
                });

                // On each draw, loop over the `detailRows` array and show any child rows
                partDataTable.on('draw', function () {
                    detailRows.forEach(function(id, i) {
                        $('#' + id + ' td.details-control').trigger('click');
                    });
                });

            //Edit button show div
            $('#participantTable').on('click', '#editButton',function(e){
                 e.preventDefault();
                 var data = partDataTable.row( $(this).parents('tr')).data();
                //console.log("edit call"+JSON.stringify(data));
                var sdData=JSON.parse(data['asset']);
                $("#editData").val(JSON.stringify(sdData,undefined, 4));
//                $("#editData").val(data['asset']);
                $('#editModal').modal('show');
            });

            //Delete  button confirm and call to server for deletion
            $('#participantTable').on('click', '#deleteButton',function(e){
                 e.preventDefault();

                 var check = confirm("Are you sure you want to delete?");
                         if (check == true) {
                             //console.log("Confirm delete");
                             var data = partDataTable.row( $(this).parents('tr')).data();
                            // console.log("delete call"+JSON.stringify(data));

                                 $.ajax('/parts/'+data.id, {
                                    type: 'DELETE',  // http method
                                    contentType: "application/json;charset=utf-8",
                                    success: function (data, status, xhr) {
                                       partDataTable.ajax.reload();

                                    },
                                    error: function (jqXhr, textStatus, errorMessage) {
                                       alert(JSON.stringify(jqXhr.responseJSON));

                                    }
                                });
                         }
                         else {
                          return ;
                         }



             });

            //Edit  button  call to server for savings
            $('#submitEditButton').on('click', function(e){

                 var normalData=$("#editData").val();
                 var data=JSON.parse(normalData);
                    $.ajax('/parts/'+data['id'], {
                            type: 'PUT',  // http method
                            contentType: "application/json;charset=utf-8",
                            data: JSON.stringify(data),  // data to submit
                            success: function (data, status, xhr) {
                                $('#editModal').modal('hide');
                              partDataTable.ajax.reload();
                            },
                            error: function (jqXhr, textStatus, errorMessage) {
                                alert(JSON.stringify(jqXhr.responseJSON));
                            }
                     });
             });

            //Add new participants
            $('#submitAddButton').on('click', function(e){

                 var normalData=$("#addData").val();
                 var data=JSON.parse(normalData);
                 //console.log("data"+data);

                     $.ajax('/parts', {
                            type: 'POST',  // http method
                            contentType: "application/json;charset=utf-8",
                            data: JSON.stringify(data),  // data to submit
                            success: function (data, status, xhr) {
                              $('#addModal').modal('hide');
                              partDataTable.ajax.reload();
                            },
                            error: function (jqXhr, textStatus, errorMessage) {
                             alert(JSON.stringify(jqXhr.responseJSON));
                            }
                     });
             });

            //Button click event call
            $('#addButton').on('click', function(e){
                e.preventDefault();
                $('#addModal').modal('show');
             });

            $('.close').on('click', function(e){
                $('#editModal').modal('hide');
                $('#addModal').modal('hide');
            });

 });


 function format(d) {
  return (
         'Self-Description: ' +
         d['asset']
     );
 }
