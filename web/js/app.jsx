/**
 * NB: REMEMBER TO RE-RUN JSX IF THIS FILE IS CHANGED!
 */

/**
 * The URL where the JSON api is available.
 */
var URL = "http://" + window.location.hostname + ":8025/api/status";

/**
 * How frequently to update the page in seconds.
 */
var REFRESH_INTERVAL = 10;

/**
 * Keywords used to determine colors.
 */
Keywords = {};
Keywords.Online = ["online"];
Keywords.Offline = ["offline"];

/**
 * The header component.
 */
var Header = React.createClass({
    render: function () {
        return (
            <a href="./">
                <div className="logo">uMonitor5</div>
            </a>
        );
    }
});


var Filter = React.createClass({

    propTypes: {
        filter: React.PropTypes.string.isRequired,
        filterChangeFn: React.PropTypes.func.isRequired
    },

    filterChangeFn: function (e) {
        this.props.filterChangeFn(e.target.value);
    },

    clearFilterFn: function () {
        this.props.filterChangeFn("");
        React.findDOMNode(this.refs.filter).focus();
    },

    componentDidMount: function () {
        document.body.onkeydown = function (e) {
            if (e.ctrlKey || e.shiftKey || e.altKey || e.metaKey) {
                return;
            }
            if (e.keyCode === 27) {
                // escape key
                this.clearFilterFn();
                return;
            }
            React.findDOMNode(this.refs.filter).focus();
        }.bind(this);
    },

    render: function () {
        return (
            <div>
                <button onClick={this.clearFilterFn} className="reset">Reset</button>
                <input ref="filter"
                       type="text"
                       name="filter"
                       value={this.props.filter}
                       placeholder="filter ..."
                       autoFocus="autoFocus"
                       className="filter"
                       onChange={this.filterChangeFn}/>
            </div>
        );
    }
});

var EventTable = React.createClass({

    getInitialState: function () {
        return {sortBy: "state", ascending: true}
    },

    getSortedEvents: function () {
        var result = _.sortBy(this.props.events, this.state.sortBy);
        if (!this.state.ascending) {
            return result.reverse();
        }
        return result;
    },

    notifySortColumn: function (column) {
        return function () {
            var ascending = this.state.ascending;
            if (this.state.sortBy === column) {
                ascending = !ascending
            }
            this.setState({sortBy: column, ascending: ascending});
        }.bind(this)
    },

    icon: function (column) {
        if (this.state.sortBy === column) {
            if (this.state.ascending) {
                return "\u25BC";
            }
            return "\u25B2";
        }
        return "";
    },

    render: function () {
        return (
            <table id="events">
                <thead>
                <td onClick={this.notifySortColumn("state")} className="column-status"><a
                    href="#">State</a> {this.icon("state")}</td>
                <td onClick={this.notifySortColumn("name")}><a href="#">Name</a> {this.icon("name")}</td>
                <td onClick={this.notifySortColumn("transition")}><a href="#">Transition</a> {this.icon("transition")}
                </td>
                <td onClick={this.notifySortColumn("time")}><a href="#">Time</a> {this.icon("time")}</td>
                <td onClick={this.notifySortColumn("message")}><a href="#">Message</a> {this.icon("message")}</td>
                </thead>
                <tbody>
                {this.getSortedEvents().map(function (event) {
                    return <EventRow key={event.name} event={event}/>
                })}
                </tbody>
            </table>
        );
    }
});

var EventRow = React.createClass({
    render: function () {
        var label = "";

        if (this.props.event.state.indexOf("Offline") === -1) {
            label = "up";
        } else {
            label = "dn";
        }

        var currentTime = moment();
        var eventTime = moment.unix(this.props.event.time);

        var formattedTime = "";

        if (currentTime.subtract(24, "hours").isAfter(eventTime)) {
            formattedTime = eventTime.format('YYYY-MM-DD HH:mm');
        } else {
            formattedTime = eventTime.fromNow();
        }

        return (
            <tr>
                <td className="column-status">
                    <div className={label}>{this.props.event.state}</div>
                </td>
                <td className="column-name">{this.props.event.name}</td>
                <td className="column-transition">{this.props.event.transition.src} &rarr; {this.props.event.transition.dst}</td>
                <td className="column-time">{formattedTime}</td>
                <td className="column-message">{this.props.event.transition.message}</td>
            </tr>
        );

    }
});

/**
 * The footer component.
 */
var Footer = React.createClass({
    render: function () {
        return (
            <div id="footer">
                <a href="https://github.com/magnus-madsen/umonitor5"> <b>uMonitor5</b> </a> by <a
                href="http://plg.uwaterloo.ca/~mmadsen/">
                Magnus Madsen </a>
            </div>
        );
    }
});

/**
 * The app component.
 */
var App = React.createClass({
    getInitialState: function () {
        return {color: "green", events: [], filter: ""};
    },

    /**
     * Changes the icon of the page.
     */
    changeIcon: function (color) {
        if (color === "green") {
            $("#icon").attr("href", "img/icon/green.png");
        } else if (color === "yellow") {
            $("#icon").attr("href", "img/icon/yellow.png");
        } else if (color === "red") {
            $("#icon").attr("href", "img/icon/red.png");
        } else {
            $("#icon").attr("href", "img/icon/grey.png");
        }
    },

    /**
     * Changes the title of the page.
     */
    changeTitle: function (color) {
        if (color === "green") {
            $(document).attr("title", "uMonitor5")
        } else if (color === "yellow") {
            $(document).attr("title", "uMonitor5 (!)")
        } else if (color === "red") {
            $(document).attr("title", "uMonitor5 (!!!)")
        } else {
            $(document).attr("title", "uMonitor5 (???)")
        }
    },

    /**
     * Returns the color to be used based on the targets.
     */
    getColor: function (targets) {
        var online = 0;
        var offline = 0;
        var unknown = 0;

        _.each(targets, function (target) {
            var stateName = target.state;

            var someOnline = _.some(Keywords.Online, function (keyword) {
                return stateName.toLowerCase().indexOf(keyword) !== -1;
            });

            var someOffline = _.some(Keywords.Offline, function (keyword) {
                return stateName.toLocaleLowerCase().indexOf(keyword) !== -1;
            });

            if (someOnline) {
                online++;
            } else if (someOffline) {
                offline++;
            } else {
                unknown++;
            }
        });

        if (offline === 0) {
            return "green";
        } else if (offline < 3) {
            return "yellow";
        } else {
            return "red";
        }
    },

    /**
     * Repeatedly reload data from the REST server.
     */
    tick: function () {
        $.ajax({
            method: "GET", dataType: 'json', url: URL, success: function (data) {
                var color = this.getColor(data);
                this.changeIcon(color);
                this.changeTitle(color);
                this.setState({"color": color, events: data});
            }.bind(this),
            error: function () {
                this.changeIcon("grey");
                this.changeTitle("grey");
                this.setState({"color": "grey"});
            }.bind(this)
        });
    },

    componentDidMount: function () {
        this.tick();
        setInterval(this.tick, REFRESH_INTERVAL * 1000);
    },

    filterChangeFn: function (filter) {
        this.setState({filter: filter});
    },

    getFilteredRows: function (rows, filter) {
        if (filter.trim() === "") {
            return rows;
        }

        var needles = filter.split(" ");
        return _.filter(rows, function (row) {
            return _.every(needles, function (needle) {
                if (needle.trim() === "") {
                    return true;
                }

                var matchesName = row.name.toLowerCase().indexOf(needle.toLowerCase()) !== -1;
                var matchesState = row.state.toLowerCase().indexOf(needle.toLowerCase()) !== -1;
                var matchesMessage = typeof row.transition.message === "string"
                    && row.transition.message.toLocaleLowerCase().indexOf(needle.toLowerCase()) !== -1;

                return matchesName || matchesState || matchesMessage;
            });
        });
    },

    render: function () {
        var className = "app-" + this.state.color;
        return (
            <div id="app" className={className}>

                <div id="page">
                    <Header color={this.state.color}/>

                    <Filter filter={this.state.filter} filterChangeFn={this.filterChangeFn}/>

                    <div id="layout">
                        <EventTable events={this.getFilteredRows(this.state.events, this.state.filter)}/>
                    </div>
                    <Footer/>
                </div>
            </div>
        );
    }
});

/**
 * Render app when the page is ready.
 */
$(document).ready(function () {
    React.render(React.createElement(App, null), document.body);
});
