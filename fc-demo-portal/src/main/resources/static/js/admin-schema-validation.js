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

  function renderOwlExpand(data, type, row) {
    if (row.type !== 'OWL') {
      return '';
    }
    return $('<button>', {
      type: 'button',
      class: 'btn btn-sm btn-link p-0 sv-owl-expand',
      title: 'Show ontologies consulted when OWL is on',
      'aria-expanded': 'false'
    })
      .append($('<i>', { class: 'bi bi-chevron-right' }))
      .prop('outerHTML');
  }

  function renderContributions(contributions) {
    var $container = $('<span>');
    var keys = Object.keys(contributions || {});
    if (keys.length === 0) {
      return $container.append(
        $('<span>', { class: 'text-muted small' })
          .text('No subclasses contributed to any registered role')
      ).prop('outerHTML');
    }
    keys.sort();
    keys.forEach(function(role, idx) {
      $container.append(
        $('<span>', { class: 'badge bg-primary-subtle text-primary-emphasis me-1' })
          .text('+' + contributions[role] + ' ' + role)
      );
    });
    return $container.prop('outerHTML');
  }

  function renderUploadedAt(data) {
    if (!data) {
      return '';
    }
    var d = new Date(data);
    return isNaN(d.getTime()) ? data : d.toISOString().substring(0, 10);
  }

  var ontologyTableInstance = null;

  function loadOntologyImpact(owlEnabled) {
    $.getJSON('/admin/schema-validation/ontologies')
      .done(function(data) {
        var items = (data && data.items) ? data.items : [];
        if (ontologyTableInstance) {
          ontologyTableInstance.clear().rows.add(items).draw();
        } else {
          ontologyTableInstance = $('#owlOntologyTable').DataTable({
            data: items,
            paging: false,
            searching: false,
            info: false,
            language: { emptyTable: 'No ontologies uploaded.' },
            columns: [
              { data: 'name', render: $.fn.dataTable.render.text() },
              { data: 'uploadedAt', render: renderUploadedAt },
              {
                data: 'contributions',
                orderable: false,
                render: renderContributions
              }
            ]
          });
        }
        applyOwlEnabledStyle(owlEnabled);
      })
      .fail(function(xhr) {
        showError('Failed to load ontology list: ' + describeAjaxError(xhr));
      });
  }

  function applyOwlEnabledStyle(owlEnabled) {
    var $panel = $('#owlOntologyPanel');
    var $hint = $('#owlOntologyPanelHint');
    if (owlEnabled) {
      $panel.removeClass('bg-light text-muted');
      $panel.css('opacity', '1');
      $hint.text('');
    } else {
      $panel.addClass('bg-light text-muted');
      $panel.css('opacity', '0.65');
      $hint.text('OWL module disabled — these ontologies are not consulted');
    }
  }

  function isOwlEnabled() {
    var $toggle = $('#svTable').find('.sv-toggle[data-type="OWL"]');
    return $toggle.length > 0 ? $toggle.is(':checked') : true;
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
          { data: null, orderable: false, render: renderOwlExpand },
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

    // Module toggle handler
    $('#svTable').on('change', '.sv-toggle', function() {
      var $toggle = $(this);
      var type = $toggle.data('type');
      var enabled = $toggle.is(':checked');

      $.ajax({
        url: '/admin/schema-validation/modules/' + encodeURIComponent(type)
          + '?enabled=' + enabled,
        type: 'PUT',
        success: function() {
          if (type === 'OWL' && $('#owlOntologyPanel').is(':visible')) {
            applyOwlEnabledStyle(enabled);
          }
        },
        error: function(xhr) {
          $toggle.prop('checked', !enabled);
          showError('Failed to update module status: ' + describeAjaxError(xhr));
        }
      });
    });

    // OWL row expand/collapse
    $('#svTable').on('click', '.sv-owl-expand', function() {
      var $btn = $(this);
      var $icon = $btn.find('i');
      var $panel = $('#owlOntologyPanel');
      var expanded = $btn.attr('aria-expanded') === 'true';

      if (expanded) {
        $panel.hide();
        $icon.removeClass('bi-chevron-down').addClass('bi-chevron-right');
        $btn.attr('aria-expanded', 'false');
      } else {
        $panel.show();
        $icon.removeClass('bi-chevron-right').addClass('bi-chevron-down');
        $btn.attr('aria-expanded', 'true');
        loadOntologyImpact(isOwlEnabled());
      }
    });
  }

});
