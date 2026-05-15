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
    // Use .attr('checked', ...) — not .prop() — because the resulting element
    // is serialised via .prop('outerHTML'), which reflects the HTML attribute
    // (default-checked state), not the live DOM property. .prop('checked')
    // sets the property but the serialised HTML never includes the checked
    // attribute, so every toggle renders as off regardless of state.
    var $input = $('<input>', {
      type: 'checkbox',
      class: 'form-check-input sv-toggle'
    }).attr('data-type', row.type);
    if (data) {
      $input.attr('checked', 'checked');
    }
    return $('<div>', { class: 'form-check form-switch' })
      .append($input)
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
        $('<span>', { class: 'badge bg-info me-1' })
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
    if (!isNaN(d.getTime())) {
      return d.toISOString().substring(0, 10);
    }
    return $('<span>').text(String(data)).prop('outerHTML');
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

  function loadSchemaValidation() {
    $.getJSON('/admin/schema-validation', function(data) {
      var allModules = data.modules || [];
      var validators = allModules.filter(function(m) { return m.type !== 'OWL'; });
      var owls = allModules.filter(function(m) { return m.type === 'OWL'; });

      var validatorCount = validators.reduce(function(sum, m) {
        return sum + (m.schemaCount || 0);
      }, 0);
      var ontologyCount = owls.reduce(function(sum, m) {
        return sum + (m.schemaCount || 0);
      }, 0);
      $('#validatorSchemaCount').text(validatorCount);
      $('#ontologySchemaCount').text(ontologyCount);

      $('#svValidatorTable').DataTable({
        data: validators,
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

      $('#svOwlTable').DataTable({
        data: owls,
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

      var owlEnabled = owls.length > 0 ? owls[0].enabled !== false : true;
      loadOntologyImpact(owlEnabled);
    }).fail(function(xhr) {
      $('#admin-content').html(
        $('<div>', { class: 'alert alert-danger' })
          .text('Failed to load schema validation status: ' + describeAjaxError(xhr))
          .prop('outerHTML'));
    });

    // Module toggle handler — bound on both tables; the closest enclosing
    // table is resolved per click so the right DataTable instance is invalidated.
    $('#svValidatorTable, #svOwlTable').on('change', '.sv-toggle', function() {
      var $toggle = $(this);
      var type = $toggle.data('type');
      var enabled = $toggle.is(':checked');
      var $table = $toggle.closest('table');

      $.ajax({
        url: '/admin/schema-validation/modules/' + encodeURIComponent(type)
          + '?enabled=' + enabled,
        type: 'PUT',
        success: function() {
          // Re-render the row so renderSchemaCount picks up the new enabled state
          // and the badge styling reflects whether stored schemas are consulted.
          var table = $table.DataTable();
          var row = table.row($toggle.closest('tr'));
          if (row.any()) {
            row.data().enabled = enabled;
            row.invalidate('data').draw(false);
          }
          if (type === 'OWL') {
            applyOwlEnabledStyle(enabled);
          }
        },
        error: function(xhr) {
          $toggle.prop('checked', !enabled);
          showError('Failed to update module status: ' + describeAjaxError(xhr));
        }
      });
    });

  }

});
