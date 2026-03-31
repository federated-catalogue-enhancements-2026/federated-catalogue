$(document).ready(function() {

 var $container = $('#rolesTable').closest('.container');

 var result = [];
 var roleDataTable ;

                     $.ajax({
                         type: "GET",
                         url: "roles",
                         datatype: 'json',
                         success: function (response) {
                             $.each(response, function(index, item) {
                               // access the properties of each user
                              var obj = { "name" : item
                                       }
                                     result.push(obj);
                             });
                             partDataTable = $('#rolesTable').DataTable({
                                             data : result,
                                             columns: [
                                                        { data: 'name' }

                                                       ]
                                            });
                             // data loaded
                         },
                         error: function (xhr, error, thrown) {
                             var msg = 'You do not have permission to view roles.';
                             var cls = 'alert-warning';
                             if (xhr.status !== 403) {
                                 msg = (xhr.responseJSON && xhr.responseJSON.message) || 'Failed to load roles.';
                                 cls = 'alert-danger';
                             }
                             $container.children().remove();
                             $container.html('<div class="alert ' + cls + ' mt-3">' + msg + '</div>');
                         }
                      });


    //edit add and delete functionality is not present in the roles data

//
//            $('#rolesTable').on('click', '#editButton',function(e){
//                 e.preventDefault();
//                 var data = roleDataTable.row( $(this).parents('tr')).data();
//                //console.log("edit call"+JSON.stringify(data));
//                $("#editData").val(JSON.stringify(data,null, ' '));
//                $('#editModal').modal('show');
//            });
//
//            $('#rolesTable').on('click', '#deleteButton',function(e){
//                 e.preventDefault();
//
//                 var check = confirm("Are you sure you want to delete?");
//                         if (check == true) {
//                             console.log("Confirm delete");
//                             var data = roleDataTable.row( $(this).parents('tr')).data();
//                             console.log("delete call"+JSON.stringify(data));
//
//                                 $.ajax('/parts/'+data.id, {
//                                    type: 'DELETE',  // http method
//                                    contentType: "application/json;charset=utf-8",
//                                    success: function (data, status, xhr) {
//                                        console.log("post delete success");
//                                        roleDataTable.ajax.reload();
//
//                                    },
//                                    error: function (jqXhr, textStatus, errorMessage) {
//                                        console.log("post delete failure");
//
//                                    }
//                                });
//                         }
//                         else {
//                          console.log("Cancel delete");
//                             return ;
//                         }
//
//
//
//             });
//
//            $('#submitEditButton').on('click', function(e){
//
//                 var normalData=$("#editData").val();
//                 var data=JSON.parse(normalData);
//                 //console.log("data"+data);
//                 console.log("data::"+data['id']);
//                     $.ajax('/parts/'+data['id'], {
//                            type: 'PUT',  // http method
//                            contentType: "application/json;charset=utf-8",
//                            data: JSON.stringify(data),  // data to submit
//                            success: function (data, status, xhr) {
//                             console.log("post edit success");
//                              $('#editModal').modal('hide');
//                              roleDataTable.ajax.reload();
//                            },
//                            error: function (jqXhr, textStatus, errorMessage) {
//                             console.log("post edit failure");
//                            }
//                     });
//             });
//
//            $('#submitAddButton').on('click', function(e){
//
//                 var normalData=$("#addData").val();
//                 var data=JSON.parse(normalData);
//                 console.log("data"+data);
//
//                     $.ajax('/parts', {
//                            type: 'POST',  // http method
//                            contentType: "application/json;charset=utf-8",
//                            data: JSON.stringify(data),  // data to submit
//                            success: function (data, status, xhr) {
//                             console.log("post edit success");
//                              $('#addModal').modal('hide');
//                              roleDataTable.ajax.reload();
//                            },
//                            error: function (jqXhr, textStatus, errorMessage) {
//                             console.log("post edit failure");
//                            }
//                     });
//             });
//
//            $('#addButton').on('click', function(e){
//                e.preventDefault();
//                $('#addModal').modal('show');
//             });
//
//            $('#addButton').on('click', function(e){
//                 e.preventDefault();
//                console.log("Add call");
//            });
//
//            $('.close').on('click', function(e){
//                $('#editModal').modal('hide');
//                $('#addModal').modal('hide');
//            });

 });