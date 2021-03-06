/**
 * Copyright 2015 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Controller for Publish Pipeline to  Modal.
 */

angular
  .module('dataCollectorApp.home')
  .controller('PublishModalInstanceController', function ($scope, $modalInstance, pipelineInfo, api, $q, authService) {
    angular.extend($scope, {
      common: {
        errors: []
      },
      commitPipelineModel : {
        name: pipelineInfo.name,
        commitMessage: ''
      },
      publish : function () {
        $q.when(api.remote.publishPipeline(
          authService.getRemoteBaseUrl(),
          authService.getSSOToken(),
          pipelineInfo.name,
          $scope.commitPipelineModel)
        ).
        then(
          function(metadata) {
            $modalInstance.close(metadata);
          },
          function(res) {
            if (res && res.status === 403) {
              $scope.common.errors =
                ['User Not Authorized. Contact your administrator to request permission to use this functionality'];
            } else {
              $scope.common.errors = [res.data];
            }
          }
        );
      },
      cancel : function () {
        $modalInstance.dismiss('cancel');
      }
    });
  });
