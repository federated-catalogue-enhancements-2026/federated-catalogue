$(document).ready(function() {

  var usersDataTable = $('#usersTable').DataTable({
    ajax: {
      url: 'users',
      error: function(xhr) {
        var msg = 'You do not have permission to view users.';
        var cls = 'alert-warning';
        if (xhr.status !== 403) {
          msg = (xhr.responseJSON && xhr.responseJSON.message) || 'Failed to load users.';
          cls = 'alert-danger';
        }
        $('#error-area').html('<div class="alert ' + cls + ' mt-2">' + msg + '</div>');
        $('#usersTable').hide();
      },
      dataSrc: function(json) {
        return json.items || [];
      }
    },
    dom: "<'row mb-2 align-items-center'<'col-md-6'f><'col-md-6 d-flex justify-content-end'l>><'row'<'col-sm-12'tr>><'row mt-2'<'col-md-5'i><'col-md-7 d-flex justify-content-end'p>>",
    initComplete: addSearchIcon,
    columns: [
      { data: 'id' },
      { data: 'participantId' },
      { data: 'firstName' },
      { data: 'lastName' },
      { data: 'email' },
      { data: 'roleIds' },
      { data: 'username' },
      {
        orderable: false,
        render: function() {
          return '<button type="button" class="btn btn-sm btn-success" id="editButton">Edit</button>';
        }
      },
      {
        orderable: false,
        render: function() {
          return '<button type="button" class="btn btn-sm btn-danger" id="deleteButton">Delete</button>';
        }
      }
    ]
  });

  $('#usersTable').on('click', '#editButton', function(e) {
    e.preventDefault();
    var data = usersDataTable.row($(this).parents('tr')).data();
    $('#editData').val(JSON.stringify(data, null, ' '));
    bootstrap.Modal.getOrCreateInstance(document.getElementById('editModal')).show();
  });

  $('#usersTable').on('click', '#deleteButton', function(e) {
    e.preventDefault();
    if (!confirm('Are you sure you want to delete?')) return;
    var data = usersDataTable.row($(this).parents('tr')).data();
    $.ajax('/users/' + data.id, {
      type: 'DELETE',
      contentType: 'application/json;charset=utf-8',
      success: function() { usersDataTable.ajax.reload(); },
      error: function(jqXhr) { alert(JSON.stringify(jqXhr.responseJSON)); }
    });
  });

  $('#submitEditButton').on('click', function() {
    var dataObj = JSON.parse($('#editData').val());
    $.ajax('/users/' + dataObj['id'], {
      type: 'PUT',
      contentType: 'application/json',
      data: JSON.stringify(dataObj),
      success: function() {
        bootstrap.Modal.getInstance(document.getElementById('editModal')).hide();
        usersDataTable.ajax.reload();
      },
      error: function(jqXhr) { alert(JSON.stringify(jqXhr.responseJSON)); }
    });
  });

  $('#submitAddButton').on('click', function() {
    var data = JSON.parse($('#addData').val());
    $.ajax('/users', {
      type: 'POST',
      contentType: 'application/json;charset=utf-8',
      data: JSON.stringify(data),
      success: function() {
        bootstrap.Modal.getInstance(document.getElementById('addModal')).hide();
        usersDataTable.ajax.reload();
      },
      error: function(jqXhr) { alert(JSON.stringify(jqXhr.responseJSON)); }
    });
  });

  $('#addButton').on('click', function(e) {
    e.preventDefault();
    bootstrap.Modal.getOrCreateInstance(document.getElementById('addModal')).show();
  });

});

function addSearchIcon() {
  var $filter = $(this.api().table().container()).find('.dataTables_filter label');
  var $input = $filter.find('input').detach();
  $filter.empty().append(
    $('<div class="input-group input-group-sm">').append(
      $('<span class="input-group-text"><i class="bi bi-search"></i></span>'),
      $input
    )
  );
}
