/*
 * Copyright © 2018 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

const tour = new Shepherd.Tour({
  defaults: {
    classes: 'shepherd-theme-dark',
    scrollTo: true
  }
});

tour
  .addStep('welcome', {
    title: 'Welcome to Data Preparation',
    text: 'Data Preparation is a tool to prepare your data in a visual way',
    attachTo: '#dataprep-navbar bottom'
  })
  // // browse file
  .addStep('file-browser', {
    title: 'Browse your file system',
    text: ['Here you can browse your files.', 'When you see a file, you can click on it to view the data'],
    attachTo: '.directory-content-table top'
  })
  .addStep('folder-example', {
    title: 'Folder',
    text: 'This is an example of a folder',
    attachTo: {
      element: '.type-icon.folder-icon',
      on: 'top'
    }
  })
  .addStep('file-example', {
    title: 'File',
    text: 'This is an example of a file',
    attachTo: {
      element: '.type-icon.file-icon',
      on: 'top'
    }
  })
  .addStep('search', {
    title: 'Search',
    text: 'You can also search for the name of the folder or file',
    attachTo: '.search-container left'
  })

  // Add connections
  .addStep('add-connection', {
    title: 'Add Connection',
    text: ['You can also add a connection to other data sources', 'Click on the <i>Add Connection</i> to continue.'],
    attachTo: '#add-connection-button right',
    buttons: false,
    when: {
      show: () => {
        const elem = document.getElementById('add-connection-button');

        const func = () => {
          setTimeout(() => {
            tour.next();

            elem.removeEventListener('click', func);
          }, 500);
        };

        elem.addEventListener('click', func);
      }
    }
  })
  .addStep('choose-connection', {
    title: 'Choose your data source',
    text: 'Here are the data sources you can add a connection to.',
    attachTo: '#dataprep-add-connection-popover right',
    buttons: false,
    when: {
      show: () => {
        const func = () => {
          tour.hide();

          document.body.removeEventListener('click', func);
        };

        document.body.addEventListener('click', func);
      }
    }
  });

export function startTour() {
  tour.start();
}
