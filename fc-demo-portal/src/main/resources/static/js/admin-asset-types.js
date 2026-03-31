$(document).ready(function() {

  var allowedTypes = [];
  var enabled = false;

  $.ajax({
    url: '/admin/me', type: 'GET',
    success: function() {
      $('#admin-content').show();
      loadConfig();
      loadExistingTypes();
    }
  });

  function loadConfig() {
    $.getJSON('/admin/asset-types', function(data) {
      enabled = data.enabled || false;
      allowedTypes = data.allowedTypes || [];
      $('#restrictionToggle').prop('checked', enabled);
      $('#types-section').toggle(enabled);
      renderTags();
      checkEmptyWarning();
    });
  }

  function loadExistingTypes() {
    $.getJSON('/admin/asset-types/existing', function(data) {
      var types = data || [];

      // Populate datalist for input autocomplete
      var list = $('#existingTypesList');
      list.empty();
      types.forEach(function(t) {
        list.append('<option value="' + t + '">');
      });

      // Render visible badges
      var container = $('#existing-type-tags');
      container.empty();
      if (types.length === 0) {
        container.append('<span class="text-muted fst-italic">No assets uploaded yet</span>');
      } else {
        types.forEach(function(t) {
          container.append(
            '<span class="badge bg-secondary me-1 mb-1" style="font-size:0.85rem;">' +
            t + '</span>'
          );
        });
      }
    });
  }

  function renderTags() {
    var container = $('#type-tags');
    container.empty();
    allowedTypes.forEach(function(type) {
      container.append(
        '<span class="badge bg-primary me-1 mb-1" style="font-size:0.9rem;">' +
        type +
        ' <button type="button" class="btn-close btn-close-white ms-1 remove-type" ' +
        'data-type="' + type + '" style="font-size:0.6rem;"></button>' +
        '</span>'
      );
    });
  }

  function checkEmptyWarning() {
    $('#empty-warning').toggle(enabled && allowedTypes.length === 0);
  }

  function saveConfig() {
    $.ajax({
      url: '/admin/asset-types',
      type: 'PUT',
      contentType: 'application/json',
      data: JSON.stringify({ enabled: enabled, allowedTypes: allowedTypes }),
      error: function() { alert('Failed to save asset type configuration.'); }
    });
  }

  function addType(type) {
    type = type.trim();
    if (!type || allowedTypes.includes(type)) return;
    allowedTypes.push(type);
    renderTags();
    checkEmptyWarning();
    saveConfig();
  }

  // Toggle handler
  $('#restrictionToggle').on('change', function() {
    enabled = $(this).is(':checked');
    $('#types-section').toggle(enabled);
    checkEmptyWarning();
    saveConfig();
  });

  // Add type button
  $('#addTypeBtn').on('click', function() {
    addType($('#newTypeInput').val());
    $('#newTypeInput').val('');
  });

  // Enter key in input
  $('#newTypeInput').on('keypress', function(e) {
    if (e.which === 13) {
      e.preventDefault();
      addType($(this).val());
      $(this).val('');
    }
  });

  // Remove type
  $('#type-tags').on('click', '.remove-type', function() {
    var type = $(this).data('type');
    allowedTypes = allowedTypes.filter(function(t) { return t !== type; });
    renderTags();
    checkEmptyWarning();
    saveConfig();
  });

  // Quick-select buttons
  $('.quick-type').on('click', function() {
    addType($(this).data('type'));
  });

});
