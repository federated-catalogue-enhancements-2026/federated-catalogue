$(document).ready(function() {

  // Admin access check
  $.ajax({
    url: '/admin/me',
    type: 'GET',
    success: function() {
      $('#admin-content').show();
      loadSchemaValidation();
    },
    error: function() {
      $('#admin-content').html('<div class="alert alert-danger">Access denied.</div>');
    }
  });

  function describeAjaxError(xhr) {
    if (xhr && xhr.responseJSON && xhr.responseJSON.message) {
      return xhr.responseJSON.message;
    }
    if (xhr && xhr.status) {
      return 'HTTP ' + xhr.status + (xhr.statusText ? ' ' + xhr.statusText : '');
    }
    return 'unknown error';
  }

  function showError(message) {
    var $banner = $('#sv-error-banner');
    if ($banner.length === 0) {
      $banner = $('<div>', {
        id: 'sv-error-banner',
        class: 'alert alert-danger alert-dismissible fade show',
        role: 'alert'
      })
        .append($('<span>', { class: 'sv-error-text' }))
        .append(
          $('<button>', {
            type: 'button',
            class: 'btn-close',
            'data-bs-dismiss': 'alert',
            'aria-label': 'Close'
          })
        );
      $('#admin-content').prepend($banner);
    }
    $banner.find('.sv-error-text').text(message);
    $banner.removeClass('d-none');
  }

  function renderModuleToggle(data, type, row) {
    return $('<div>', { class: 'form-check form-switch' })
      .append(
        $('<input>', {
          type: 'checkbox',
          class: 'form-check-input sv-toggle'
        })
          .attr('data-type', row.type)
          .prop('checked', !!data)
      )
      .prop('outerHTML');
  }

  function renderSchemaCount(data, type, row) {
    var badgeClass = row.enabled
      ? 'badge bg-secondary'
      : 'badge bg-light text-muted border';
    var title = row.enabled
      ? ''
      : 'Module disabled — stored schemas are not consulted';
    return $('<span>', { class: badgeClass, title: title })
      .text(data == null ? '0' : String(data))
      .prop('outerHTML');
  }

  function loadSchemaValidation() {
    $.getJSON('/admin/schema-validation', function(data) {
      $('#totalSchemaCount').text(data.totalSchemaCount || 0);

      $('#svTable').DataTable({
        data: data.modules || [],
        paging: false,
        searching: false,
        info: false,
        columns: [
          { data: 'name', render: $.fn.dataTable.render.text() },
          { data: 'description', render: $.fn.dataTable.render.text() },
          { data: 'schemaCount', render: renderSchemaCount },
          { data: 'enabled', render: renderModuleToggle }
        ]
      });
    }).fail(function(xhr) {
      $('#admin-content').html(
        $('<div>', { class: 'alert alert-danger' })
          .text('Failed to load schema validation status: ' + describeAjaxError(xhr))
          .prop('outerHTML'));
    });

    // Toggle handler
    $('#svTable').on('change', '.sv-toggle', function() {
      var $toggle = $(this);
      var type = $toggle.data('type');
      var enabled = $toggle.is(':checked');

      $.ajax({
        url: '/admin/schema-validation/modules/' + encodeURIComponent(type)
          + '?enabled=' + enabled,
        type: 'PUT',
        error: function(xhr) {
          $toggle.prop('checked', !enabled);
          showError('Failed to update module status: ' + describeAjaxError(xhr));
        }
      });
    });
  }

});
