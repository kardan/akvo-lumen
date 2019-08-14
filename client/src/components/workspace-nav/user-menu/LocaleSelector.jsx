import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';

import { changeLocale } from '../../../actions/locale';

require('./LocaleSelector.scss');

const availableLocales = [{
  label: 'English',
  tag: 'en',
}, {
  label: 'Espanol',
  tag: 'es',
}, {
  label: 'Francais',
  tag: 'fr',
}];

const LocaleSelectorItem = ({ locale, currentLocale, handleLocaleSelection }) => {
  const styleClasses = ['LocaleSelectorItem'];
  if (locale.tag === currentLocale) {
    styleClasses.push('LocaleSelectorItemSelected');
  }
  const classNames = styleClasses.join(' ');
  return (
    <a onClick={handleLocaleSelection} className={classNames} data-value={locale.tag}>
      { locale.label }
    </a>
  );
};

LocaleSelectorItem.propTypes = {
  locale: PropTypes.object,
  currentLocale: PropTypes.string,
  handleLocaleSelection: PropTypes.func.isRequired,
};

class LocaleSelector extends React.Component {
  constructor(props) {
    super(props);
    // this.state = { locale: this.props };
    this.handleLocaleSelection = this.handleLocaleSelection.bind(this);
  }

  handleLocaleSelection(e) {
    const currentLocale = this.props.locale;
    const newLocale = e.currentTarget.dataset.value;
    if (currentLocale !== newLocale) {
      this.props.dispatch(changeLocale(newLocale));
    }
  }

  render() {
    const currentLocale = this.props.locale;
    return (
      <div>
        {availableLocales.map(locale => (
          <LocaleSelectorItem
            key={locale.tag}
            locale={locale}
            handleLocaleSelection={this.handleLocaleSelection}
            currentLocale={currentLocale}
          />
        ))}
      </div>
    );
  }
}

LocaleSelector.propTypes = {
  locale: PropTypes.string,
  dispatch: PropTypes.func,
};

function mapStateToProps(state) {
  return {
    locale: state.profile.attributes.locale[0],
  };
}
export default connect(mapStateToProps)(LocaleSelector);
