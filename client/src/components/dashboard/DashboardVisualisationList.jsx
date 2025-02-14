import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { FormattedMessage } from 'react-intl';
import { getDataLastUpdated } from '../../utilities/chart';
import { getIconUrl } from '../../domain/entity';
import { specIsValidForApi } from '../../utilities/aggregation';

require('./DashboardVisualisationList.scss');

const filterVisualisations = (visualisations, filterText) => {
  // NB - this naive approach is fine with a few hundred visualisations, but we should replace
  // with something more serious before users start to have thousands of visualisations

  if (!filterText) {
    return visualisations.filter(({ spec, visualisationType }) =>
      specIsValidForApi(spec, visualisationType)
    );
  }

  return visualisations.filter((visualisation) => {
    if (!specIsValidForApi(visualisation.spec, visualisation.visualisationType)) {
      return false;
    }

    let name = visualisation.name || '';
    name = name.toString().toLowerCase();

    return name.indexOf(filterText.toString().toLowerCase()) > -1;
  });
};

export default class DashboardVisualisationList extends Component {
  constructor() {
    super();
    this.state = {
      filterText: '',
    };
  }

  render() {
    const { props } = this;
    const isOnDashboard = item => Boolean(props.dashboardItems[item.id]);
    let visualisations = props.visualisations.slice(0);
    const showFilterInput = visualisations.length > 5;

    visualisations = filterVisualisations(visualisations, this.state.filterText);
    visualisations.sort((a, b) => b.modified - a.modified);

    return (
      <div
        className="DashboardVisualisationList"
      >
        {props.visualisations.length === 0 ?
          <div
            className="noVisualisationsMessage"
          >
            <FormattedMessage id="no_visualisations_to_show" />
          </div>
          :
          <div>
            {showFilterInput &&
              <div className="filterInput">
                <label
                  htmlFor="filterText"
                >
                  <FormattedMessage id="filter_list_by_title" />
                </label>
                <input
                  type="text"
                  name="filterText"
                  placeholder="Visualisation title"
                  value={this.state.filterText}
                  onChange={evt => this.setState({ filterText: evt.target.value })}
                />
              </div>
            }
            <ul className="list">
              {visualisations.map((item) => {
                const dataLastUpdated = getDataLastUpdated({
                  visualisation: item,
                  datasets: props.datasets,
                });
                return (
                  <li
                    className={`listItem clickable ${item.visualisationType.replace(' ', '')}
                    ${isOnDashboard(item) ? 'added' : ''}`}
                    data-test-name={item.name}
                    key={item.id}
                    onClick={() => props.onEntityClick(item, 'visualisation')}
                  >
                    <div className="entityIcon">
                      <img src={getIconUrl(item)} role="presentation" />
                    </div>
                    <div className="textContent">
                      <h3>
                        {item.name}
                        <span
                          className="isOnDashboardIndicator"
                        >
                          {isOnDashboard(item) ? '✔' : ''}
                        </span>
                      </h3>

                      <div className="visualisationType">
                        {item.visualisationType === 'map' ?
                        'Map'
                        :
                        `${item.visualisationType.charAt(0).toUpperCase() +
                            item.visualisationType.slice(1)} chart`
                      }
                      </div>
                      <br />
                      {dataLastUpdated && (
                        <div className="lastModified">
                          <FormattedMessage id="data_last_updated" />
                          : {dataLastUpdated}
                        </div>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
            {(this.state.filterText && visualisations.length === 0) &&
              <div className="filterHelpText">
                <FormattedMessage id="no_visualisations_match_your_filter" />
                <div className="buttonContainer">
                  <button
                    className="clickable"
                    onClick={() => this.setState({ filterText: '' })}
                  >
                    <FormattedMessage id="clear_filter" />
                  </button>
                </div>
              </div>
            }
          </div>
        }
      </div>
    );
  }
}

DashboardVisualisationList.propTypes = {
  dashboardItems: PropTypes.object.isRequired,
  visualisations: PropTypes.array.isRequired,
  datasets: PropTypes.object.isRequired,
  onEntityClick: PropTypes.func.isRequired,
};
