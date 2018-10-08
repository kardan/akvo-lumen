/* eslint-disable react/prefer-stateless-function */
import React, { Component } from 'react';
import PropTypes from 'prop-types';
import windowSize from 'react-window-size';

import VisualisationViewer from '../charts/VisualisationViewer';

require('./VisualisationPreview.scss');

const HEADER_HEIGHT = 70;

function shouldRender(visualisation, datasets) {
  const datasetId = visualisation.datasetId;
  const dataset = datasetId ? datasets[datasetId] : null;
  const datasetLoaded = dataset ? Boolean(dataset.get('columns')) : false;
  const vType = visualisation.visualisationType;
  const { spec } = visualisation;

  switch (vType) {
    case 'map':
      return true;

    case 'pivot table':
      if (!datasetLoaded) {
        return false;
      }
      break;

    case 'bar':
      if (!datasetLoaded) {
        return false;
      }
      if (spec.metricColumnY == null || spec.bucketColumn == null) {
        return false;
      }
      break;

    case 'line':
    case 'area':
      if (!datasetLoaded) {
        return false;
      }
      if (spec.metricColumnY == null || spec.metricColumnX == null) {
        return false;
      }
      break;

    case 'pie':
    case 'donut':
      if (!datasetLoaded) {
        return false;
      }
      if (spec.bucketColumn == null) {
        return false;
      }
      break;

    case 'scatter':
      if (!datasetLoaded) {
        return false;
      }
      if (spec.metricColumnX == null || spec.metricColumnY == null) {
        return false;
      }
      break;

    default:
      return false;
  }

  return true;
}

class VisualisationPreview extends Component {
  render() {
    const { visualisation,
      metadata,
      datasets,
      onChangeVisualisationSpec,
      windowHeight,
      width,
      height,
    } = this.props;

    return (
      <div className="VisualisationPreview">
        {shouldRender(visualisation, datasets) ?
          <VisualisationViewer
            visualisation={visualisation}
            metadata={metadata}
            datasets={datasets}
            context="editor"
            height={
              height ||
              visualisation.visualisationType === 'map' ?
                null :
                Math.min(500, windowHeight - HEADER_HEIGHT)
            }
            width={visualisation.visualisationType === 'map' ? null : (width || 800)}
            onChangeVisualisationSpec={onChangeVisualisationSpec}
          /> : null
        }
      </div>
    );
  }
}

VisualisationPreview.propTypes = {
  visualisation: PropTypes.object.isRequired,
  metadata: PropTypes.object,
  datasets: PropTypes.object.isRequired,
  onChangeVisualisationSpec: PropTypes.func,
  width: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  height: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  windowHeight: PropTypes.number,
};

export default windowSize(VisualisationPreview);
