import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { FormattedMessage, intlShape, injectIntl } from 'react-intl';
import ConfigMenuSectionOptionSelect from '../../common/ConfigMenu/ConfigMenuSectionOptionSelect';
import Button from '../../common/Button';
import SelectMenu from '../../common/SelectMenu';
import './SeriesMenu.scss';

class SeriesMenu extends Component {

  constructor() {
    super();
    this.state = {
      inputInProgress: false,
      newSeriesColumn: null,
    };
    this.toggleInput = this.toggleInput.bind(this);
    this.updateSeries = this.updateSeries.bind(this);
  }

  updateSeries(value, index) {
    if (value === null) {
      this.deleteSerie(index);
    } else if (index !== undefined) {
      this.changeSerie(value, index);
    } else {
      this.saveSerie(value);
      this.toggleInput();
    }
  }

  changeSerie(c, index) {
    const metricColumnsY = this.props.metricColumnsY.slice(0);
    metricColumnsY.splice(index, 1, c);
    this.props.onChangeSpec({
      metricColumnsY,
      axisLabelY: this.props.metricAggregation,
      axisLabelYFromUser: false,
    });
    this.setState({
      inputInProgress: false,
      newSeriesColumn: null,
    });
  }

  saveSerie(newSeriesColumn) {
    const metricColumnsY = this.props.metricColumnsY.slice(0);
    metricColumnsY.push(
      newSeriesColumn
    );
    this.props.onChangeSpec({
      metricColumnsY,
      axisLabelY: this.props.metricAggregation,
      axisLabelYFromUser: false,
    });
    this.setState({
      inputInProgress: false,
      newSeriesColumn: null,
    });
  }

  deleteSerie(index) {
    const series = this.props.metricColumnsY;
    const delSerie = series[index];
    const rawSeries = this.props.metricColumnsY;

    if (index === -1) {
      throw new Error(`Cannot delete serie ${delSerie} as it does not appear in spec.metricColumnsY`);
    } else {
      rawSeries.splice(index, 1);
      if (series.length === 0) {
        this.props.onChangeSpec({
          metricColumnsY: rawSeries,
          axisLabelY: this.props.metricColumnYTitle,
          axisLabelYFromUser: false,
        });
      } else {
        this.props.onChangeSpec({
          metricColumnsY: rawSeries,
          axisLabelY: this.props.metricAggregation,
          axisLabelYFromUser: false,
        });
      }
    }
  }

  toggleInput() {
    this.setState({
      inputInProgress: !this.state.inputInProgress,
    });
  }

  render() {
    const { hasDataset, columnOptions, intl } = this.props;
    const { formatMessage } = intl;
    const metricColumnsY = this.props.metricColumnsY;
    const columnsSet = new Set(metricColumnsY);
    const {
      newSeriesColumn,
      inputInProgress,
    } = this.state;
    return (
      <div className={`SerieMenu inputGroup ${hasDataset ? 'enabled' : 'disabled'}`}>
        <div>
          <div className="container">
            {(!metricColumnsY || metricColumnsY.length === 0) ? (
              <div className="noFilters" />
            ) : (
              <div className="filterListContainer">
                <ol className="filterList">
                  {metricColumnsY.map((metricColumnY, index) =>
                    (<div key={index}>
                      <ConfigMenuSectionOptionSelect
                        id="metric_column"
                        placeholderId="select_a_metric_column"
                        value={metricColumnY !== null ? metricColumnY : null}
                        name="metricColumnYInput"
                        clearable
                        onChange={choice => this.updateSeries(choice, index)}
                        // eslint-disable-next-line max-len
                        options={columnOptions.filter(c => !columnsSet.has(c.value) || c.value === metricColumnY)}
                      />
                    </div>)
                  )}
                </ol>
              </div>
            )}
            {inputInProgress && (
              <div>
                <div className="inputGroup">
                  <div className="filterBodyContainer">
                    <SelectMenu
                      className="filterColumnInput"
                      name="filterColumnInput"
                      placeholder={`${formatMessage({ id: 'select_a_column' })}...`}
                      value={newSeriesColumn || null}
                      clearable
                      options={columnOptions.filter(c => !columnsSet.has(c.value))}
                      onChange={choice => this.updateSeries(choice)}
                    />
                  </div>
                </div>
                {/* <div className="inputGroup">
                <div className="buttonContainer">
                  <Button
                    className="saveFilter clickable"
                    onClick={() => this.toggleInput()}
                    primary
                  >
                    <FormattedMessage id="remove_column" />
                  </Button>
                  </div>
            </div> */}
              </div>
            )}
            {!inputInProgress && (
              <Button onClick={this.toggleInput} primary>
                <i className="fa fa-plus" aria-hidden="true" />
                &nbsp;
                <FormattedMessage id="select_another_metric_column" />
              </Button>
            )}
          </div>
        </div>
      </div>
    );
  }
}

SeriesMenu.propTypes = {
  intl: intlShape,
  metricColumnsY: PropTypes.array.isRequired,
  metricColumnYTitle: PropTypes.string,
  hasDataset: PropTypes.bool.isRequired,
  columnOptions: PropTypes.array.isRequired,
  onChangeSpec: PropTypes.func.isRequired,
  metricAggregation: PropTypes.string.isRequired,
};

export default injectIntl(SeriesMenu);
