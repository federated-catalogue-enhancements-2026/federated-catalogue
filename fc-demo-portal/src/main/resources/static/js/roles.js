$(document).ready(function() {

  $.ajax({
    type: 'GET',
    url: 'roles',
    dataType: 'json',
    success: function(response) {
      var data = response.map(function(name) { return { name: name }; });
      $('#rolesTable').DataTable({
        data: data,
        dom: "<'row mb-2 align-items-center'<'col-md-6'f><'col-md-6 d-flex justify-content-end'l>><'row'<'col-sm-12'tr>><'row mt-2'<'col-md-5'i><'col-md-7 d-flex justify-content-end'p>>",
        initComplete: addSearchIcon,
        columns: [{ data: 'name' }]
      });
    },
    error: function(xhr) {
      var msg = 'You do not have permission to view roles.';
      var cls = 'alert-warning';
      if (xhr.status !== 403) {
        msg = (xhr.responseJSON && xhr.responseJSON.message) || 'Failed to load roles.';
        cls = 'alert-danger';
      }
      $('#error-area').html('<div class="alert ' + cls + ' mt-2">' + msg + '</div>');
      $('#rolesTable').hide();
    }
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
