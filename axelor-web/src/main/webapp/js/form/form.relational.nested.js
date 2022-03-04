/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2022 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
(function() {

"use strict";

var ui = angular.module("axelor.ui");

var NestedForm = {
  scope: true,
  controller: [ '$scope', '$element', function($scope, $element) {

    ui.FormViewCtrl.call(this, $scope, $element);

    $scope.onShow = function(viewPromise) {

    };

    $scope.$$forceWatch = false;
    $scope.$$forceCounter = false;

    $scope.$setForceWatch = function () {
      $scope.$$forceWatch = true;
      $scope.$$forceCounter = true;
    };

    $scope.registerNested($scope);
    $scope.show();
  }],
  link: function(scope, element, attrs, ctrl) {

  },
  template: '<div ui-view-form x-handler="this"></div>'
};

ui.EmbeddedEditorCtrl = EmbeddedEditorCtrl;
ui.EmbeddedEditorCtrl.$inject = ['$scope', '$element', 'DataSource', 'ViewService'];

function EmbeddedEditorCtrl($scope, $element, DataSource, ViewService) {

  var params = angular.copy($scope._viewParams);

  params.views = _.compact([params.summaryView || params.summaryViewDefault]);
  $scope._viewParams = params;

  ui.ViewCtrl($scope, DataSource, ViewService);
  ui.FormViewCtrl.call(this, $scope, $element);

  $scope.visible = false;
  $scope.onShow = function() {

  };

  var originalEdit = $scope.edit;

  function doEdit(record, fireOnLoad) {
    if (record && record.id > 0 && !record.$fetched) {
      Object.keys(record)
        .filter(function (name) { return name.indexOf(".") >= 0 })
        .forEach(function (name) {
          ui.setNested(record, name, record[name]);
          delete record[name];
        });
      $scope.doRead(record.id).success(function(rec){
        var updated = _.extend({}, rec, record);
        originalEdit(updated, fireOnLoad);
      });
    } else {
      originalEdit(record, fireOnLoad);
    }

    if ($scope.gridEditing) {
      return;
    }
    $scope.visible = record != null;
  }

  function clearForm() {
    doEdit(null);
  }

  function isPopulated() {
    return $scope.record != null && $scope.record.id != null;
  }

  $scope.edit = function(record, fireOnLoad) {
    if ($scope.closeForm) {
      // When we just add item, don't display MaterDetail form
      $scope.closeForm = false;
      clearForm();
      scrollToGrid();
      return;
    }
    doEdit(record, fireOnLoad);
    $scope.setEditable(!$scope.$parent.$$readonly);
  };

  function scrollToGrid() {
    var gridElem = $element.prev()[0];
    if (gridElem) {
      gridElem.scrollIntoView();
    }
  }

  function scrollToDetailView() {
    var detailViewElem = $element[0];
    if (detailViewElem) {
      detailViewElem.scrollIntoView();
    }
  }

  $scope.onClose = function() {
    clearForm();
    scrollToGrid();
  };

  $scope.onCancel = function() {
    clearForm();
    scrollToGrid();
  };

  $scope.canClose = function() {
    return $scope.isReadonly() && $scope.visible;
  }

  $scope.canCancel = function() {
    return !$scope.isReadonly() && $scope.visible;
  }

  $scope.canAdd = function() {
    return !$scope.isReadonly() && !isPopulated() && $scope.visible;
  }

  $scope.canUpdate = function() {
    return !$scope.isReadonly() && isPopulated() && $scope.visible;
  }

  $scope.canCreate = function() {
    return $scope.hasPermission("create") && !$scope.isReadonly() && $scope.isEditable() && $scope.$parent.canNew() && !$scope.visible;
  }

  $scope.onCreate = function() {
    $scope.visible = true;
    $scope.$broadcast('on:new');
    scrollToDetailView();
  }

  $scope.onOK = function() {
    if (!$scope.isValid()) {
      return;
    }
    var record = $scope.record;
    if (record) record.$fetched = true;

    var event = $scope.$broadcast('on:before-save', record);
    if (event.defaultPrevented) {
      if (event.error) {
        return axelor.dialogs.error(event.error);
      }
    }
    $scope.waitForActions(function () {
      $scope.select($scope.record);
      $scope.waitForActions(clearForm);
      scrollToGrid();
    });
  };

  $scope.onAdd = function() {
    if (!$scope.isValid() || !$scope.record) {
      return;
    }

    var record = $scope.record;
    record.id = null;
    record.version = null;
    record.$version = null;
    // make the record as selected in the grid (like in popup)
    record.selected = true;

    function doSelect(rec) {
      $scope.closeForm = true;
      if (rec) {
        $scope.select(rec);
      }
    }

    if (!$scope.editorCanSave) {
      return doSelect(record);
    }

    $scope.onSave().then(function (rec) {
      doSelect(rec);
    });
  };

  function loadSelected() {
    var record = $scope.getSelectedRecord();
    $scope.edit(record);
  }

  $scope.$on('on:edit', function(event, record) {
    if ($scope.$parent.record === record) {
      // on parent top form editing
      if($scope.visible) {
        $scope.waitForActions(loadSelected);
      } else {
        $scope.waitForActions(clearForm);
      }
    }
  });

  $scope.$parent.$watch('isReadonly()', function nestedReadonlyWatch(readonly, old) {
    if (readonly === old) return;
    $scope.setEditable(!readonly);
  });

  $scope.setDetailView = function () {
    $scope.isDetailView = true;

    $scope.$parent.$on('on:slick-editor-init', function () {
      $scope._viewParams.forceReadonly = true;
    });

    $scope.$parent.$on('on:slick-editor-change', function (e, record) {
      if ($scope.gridEditing) {
        $scope.record = record;
        $scope.$broadcast('on:record-change', record);
      }
    });

    $scope.$parent.$on('on:grid-edit-start', function () {
      $scope.$timeout(function () {
        $scope.gridEditing = true;
        $scope.visible = true;
      })
    });

    $scope.$parent.$on('on:grid-edit-end', function (e, grid, opts) {
      $scope.gridEditing = false;
      // Revert changes
      if (opts.cancel && opts.dirty && $scope.record.id) {
        $scope.waitForActions(function () {
          loadSelected();
        });
      }
    });
  }

  $scope.show();
}

var EmbeddedEditor = {
  restrict: 'EA',
  css: 'nested-editor',
  scope: true,
  controller: EmbeddedEditorCtrl,
  template:
    '<fieldset class="form-item-group bordered-box">'+
      '<div ui-view-form x-handler="this" ng-show="visible"></div>'+
      '<div class="btn-toolbar pull-right">'+
        '<button type="button" class="btn btn btn-info" ng-click="onClose()" ng-show="canClose()"><span x-translate>Close</span></button> '+
        '<button type="button" class="btn btn-danger" ng-click="onCancel()" ng-show="canCancel()"><span x-translate>Cancel</span></button> '+
        '<button type="button" class="btn btn-primary" ng-click="onAdd()" ng-show="canAdd()"><span x-translate>Ok</span></button> '+
        '<button type="button" class="btn btn-primary" ng-click="onOK()" ng-show="canUpdate()"><span x-translate>Update</span></button>'+
        '<button type="button" class="btn btn-primary" ng-click="onCreate()" ng-show="canCreate()"><span x-translate>New</span></button>'+
      '</div>'+
    '</fieldset>'
};

ui.NestedEditorCtrl = NestedEditorCtrl;
ui.NestedEditorCtrl.$inject = ['$scope', '$element', 'DataSource', 'ViewService'];

function NestedEditorCtrl($scope, $element, DataSource, ViewService) {

  var params = angular.copy($scope._viewParams);

  params.views = _.compact([params.summaryView || params.summaryViewDefault]);
  $scope._viewParams = params;

  ui.ManyToOneCtrl.call(this, $scope, $element, DataSource, ViewService);

  $scope.nested = null;
  $scope.registerNested = function(scope) {
    $scope.nested = scope;

    $scope.$watch("isReadonly()", function nestedReadonlyWatch(readonly) {
      scope.setEditable(!readonly);
    });
  };
}

var NestedEditor = {
  restrict: 'EA',
  css: 'nested-editor',
  require: '?ngModel',
  scope: true,
  controller: NestedEditorCtrl,
  link: function(scope, element, attrs, model) {

    function setValidity(nested, valid) {
      model.$setValidity('valid', nested.isValid());
      if (scope.setValidity) {
        scope.setValidity('valid', nested.isValid());
      }
    }

    var configure = _.once(function (nested) {

      //FIX: select on M2O doesn't apply to nested editor
      var unwatchId = scope.$watch(attrs.ngModel + '.id', function nestedRecordIdWatch(id, old){
        if (id === old) {
          return;
        }
        unwatchId();
        unwatchId = null;
        scope.$applyAsync();
      });

      var unwatchValid = nested.$watch('form.$valid', function nestedValidWatch(valid, old){
        if (valid === old) {
          return;
        }
        unwatchValid();
        unwatchValid = null;
        setValidity(nested, valid);
      });

      scope.$on("on:check-nested-values", function (e, value) {
        if (nested && value) {
          var val = scope.getValue() || {};
          if (val.$updatedValues === value) {
            _.extend(nested.record, value);
          }
        }
      });

      var parentAttrs = scope.$parent.field || {};
      if (parentAttrs.forceWatch) {
        nested.$$forceWatch = true;
      }
    });

    var unwatch = null;
    var original = null;

    function nestedEdit(record, fireOnLoad) {

      var nested = scope.nested;
      var counter = 0;

      if (!nested) return;
      if (unwatch) unwatch();

      original = angular.copy(record);

      unwatch = nested.$watch('record', function nestedRecordWatch(rec, old) {

        if (counter++ === 0 && !nested.$$forceCounter) {
          return;
        }

        var ds = nested._dataSource;
        var name = scope.field.name;

        // don't process default values
        if (ds.equals(rec, nested.defaultValues)) {
          return;
        }

        if (_.isEmpty(rec)) rec = null;
        if (_.isEmpty(old)) old = null;
        if (rec == old) {
          return;
        }
        if (rec) {
          rec.$dirty = !(rec.id > 0 && ds.equals(rec, original));
        }

        model.$setViewValue(rec);
        setValidity(nested, nested.isValid());
      }, true);

      return nested.edit(record, fireOnLoad);
    }

    scope.ngModel = model;
    scope.visible = false;

    scope.onClear = function() {
      scope.$parent.setValue(null, true);
      scope.$parent.$broadcast('on:new');
    };

    scope.onClose = function() {
      scope.$parent._isNestedOpen = false;
      scope.visible = false;
      element.hide();
    };

    scope.canClose = function() {
      return scope.canToggle() && scope.canSelect();
    };

    attrs.$observe('title', function(title){
      scope.title = title;
    });

    model.$render = function() {
      var nested = scope.nested,
        promise = nested._viewPromise,
        oldValue = model.$viewValue;

      function doRender() {
        var value = model.$viewValue;
        if (oldValue !== value) { // prevent unnecessary onLoad
          return;
        }
        if (!value || !value.id || value.$dirty) {
          return nestedEdit(value, false);
        }
        if (value.$fetched && (nested.record||{}).$fetched) return;
        return nested.doRead(value.id).success(function(record){
          record.$fetched = true;
          value.$fetched = true;
          return nestedEdit(_.extend({}, value, record));
        });
      }

      if (nested == null) {
        return;
      }

      promise.then(function() {
        configure(nested);
        nestedEdit(model.$viewValue, false);
        scope.waitForActions(doRender, 100);
      });
    };
  },
  template:
  '<fieldset class="form-item-group bordered-box" ui-show="visible">'+
    '<legend>'+
      '<span ng-bind-html="title"></span> '+
      '<span class="legend-toolbar" style="display: none;" ng-show="!isReadonly()">'+
        '<a href="" tabindex="-1" ng-click="onClear()" title="{{\'Clear\' | t}}" ng-show="canShowIcon(\'clear\')"><i class="fa fa-ban"></i></a> '+
        '<a href="" tabindex="-1" ng-click="onSelect()" title="{{\'Select\' | t}}" ng-show="canShowIcon(\'select\')"><i class="fa fa-search"></i></a> '+
        '<a href="" tabindex="-1" ng-click="onClose()" title="{{\'Close\' | t}}" ng-show="canClose()"><i class="fa fa-times-circle"></i></a>'+
      '</span>'+
    '</legend>'+
    '<div ui-nested-form></div>'+
  '</fieldset>'
};

ui.formDirective('uiNestedEditor', NestedEditor);
ui.formDirective('uiEmbeddedEditor', EmbeddedEditor);
ui.formDirective('uiNestedForm', NestedForm);

})();
